/*
 * Copyright (c) 2017 Antony Esik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ae.camunda.dispatcher.runtime.test;

import com.ae.camunda.dispatcher.api.annotation.CamundaVar;
import com.ae.camunda.dispatcher.api.manager.ExternalTaskManager;
import com.ae.camunda.dispatcher.model.ExternalTask;
import com.ae.camunda.dispatcher.runtime.manager.ExternalTaskManagerImpl;
import com.ae.camunda.dispatcher.runtime.test.command.Command;
import com.ae.camunda.dispatcher.runtime.test.command.WrongCommand;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.google.common.reflect.ClassPath;
import io.github.benas.randombeans.api.EnhancedRandom;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.rest.dto.VariableValueDto;
import org.camunda.bpm.engine.rest.dto.externaltask.CompleteExternalTaskDto;
import org.camunda.bpm.engine.rest.dto.externaltask.ExternalTaskFailureDto;
import org.camunda.bpm.engine.rest.dto.externaltask.FetchExternalTasksDto;
import org.camunda.bpm.engine.rest.dto.externaltask.LockedExternalTaskDto;
import org.camunda.bpm.engine.variable.impl.value.PrimitiveTypeValueImpl;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;

@RunWith(MockitoJUnitRunner.class)
public class ExternalTaskManagerImplTest {

    private ExternalTaskManagerImpl manager;

    @Before
    public void init() {
        manager = new ExternalTaskManagerImpl();

        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(ExternalTaskManagerImpl.class, "packages")
                , manager
                , Arrays.asList(Command.class.getPackage().getName())
        );
    }

    @Test
    public void testInit() {
        manager.init();

        Assert.assertEquals(17, manager.getExternalTaskFieldAnnotations().size());
        Assert.assertEquals(1, manager.getExternalTaskDefinitions().size());

        {
            ExternalTask<?> task = manager.getExternalTaskDefinitions().get(Command.TASK_NAME);
            Assert.assertNotNull(task);
            Assert.assertEquals(Command.TASK_NAME, task.getName());
            Assert.assertEquals(19, task.getFields().size());

            testFields((key) -> Assert.assertNotNull(task.getFields().get(key)));
        }

        {
            ExternalTask<?> task = manager.getExternalTaskDefinitions().get(WrongCommand.TASK_NAME);
            Assert.assertNull(task);
        }
    }

    @Test
    public void testGetExternalTaskTopics() {
        Assert.assertTrue(manager.getExternalTaskTopics(1).isEmpty());

        manager.init();

        long lockTimeuot = 1;
        List<FetchExternalTasksDto.FetchExternalTaskTopicDto> topics = manager.getExternalTaskTopics(lockTimeuot);
        Assert.assertFalse(topics.isEmpty());

        FetchExternalTasksDto.FetchExternalTaskTopicDto topic = topics.get(0);
        Assert.assertNotNull(topic);
        Assert.assertEquals(lockTimeuot, topic.getLockDuration());
        Assert.assertEquals(Command.TASK_NAME, topic.getTopicName());

        Assert.assertNotNull(topic.getVariables());
        Assert.assertEquals(19, topic.getVariables().size());

        testFields((key) -> Assert.assertTrue(topic.getVariables().contains(key)));
    }

    @Test(expected = NoSuchElementException.class)
    public void testToCommandFail() {
        manager.toCommand(new LockedExternalTaskDto());
    }

    @Test
    public void testToCommand() {
        manager.init();

        LockedExternalTaskDto task = EnhancedRandom.random(LockedExternalTaskDto.class);
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(LockedExternalTaskDto.class, "topicName")
                , task
                , Command.TASK_NAME
        );
        String stringVar = "value";
        String otherVar = "other value";

        task.getVariables().put("stringVar", VariableValueDto.fromTypedValue(new PrimitiveTypeValueImpl.StringValueImpl(stringVar)));
        task.getVariables().put("otherVar", VariableValueDto.fromTypedValue(new PrimitiveTypeValueImpl.StringValueImpl(otherVar)));

        Object object = manager.toCommand(task);
        Assert.assertNotNull(object);
        Assert.assertTrue(object instanceof Command);

        Command command = (Command) object;
        Assert.assertEquals(stringVar, command.getStringVar());
        Assert.assertEquals(otherVar, command.getAnotherStringVar());

        ReflectionUtils.doWithFields(LockedExternalTaskDto.class, field -> {
            if (field.getName().equals("variables")) {
                return;
            }

            Field commandField = ReflectionUtils.findField(Command.class, field.getName());
            Assert.assertNotNull("No field with name: " + field.getName(), commandField);
            Assert.assertEquals(
                    JavaUtils.getFieldWithoutCheckedException(field, task)
                    , JavaUtils.getFieldWithoutCheckedException(commandField, command)
            );
        });
    }

    @Test
    public void testToCompleteTask() {
        manager.init();

        Pair<String, CompleteExternalTaskDto> pair = manager.toCompleteTask(null, new Object());
        Assert.assertNull(pair);

        pair = manager.toCompleteTask(Command.TASK_NAME, null);
        Assert.assertNull(pair);

        Command command = EnhancedRandom.random(Command.class);
        pair = manager.toCompleteTask(Command.TASK_NAME, command);

        Assert.assertNotNull(pair);
        Assert.assertNotNull(pair.getKey());
        Assert.assertEquals(command.getId(), pair.getKey());

        Assert.assertNotNull(pair.getValue());
        Assert.assertEquals(command.getWorkerId(), pair.getValue().getWorkerId());

        Assert.assertNotNull(pair.getValue().getVariables());
        Assert.assertFalse(pair.getValue().getVariables().isEmpty());

        final Pair<String, CompleteExternalTaskDto> finalPair = pair;
        ReflectionUtils.doWithFields(Command.class, field -> {
            CamundaVar camundaVar = field.getAnnotation(CamundaVar.class);
            if (camundaVar == null) {
                return;
            }

            Assert.assertTrue(finalPair.getValue().getVariables().containsKey(ExternalTaskManager.getVarName(field, camundaVar)));
        });
    }

    @Test
    public void testToFailTask() {
        manager.init();

        Pair<String, ExternalTaskFailureDto> pair = manager.toFailTask(null, new Object());
        Assert.assertNull(pair);

        pair = manager.toFailTask(Command.TASK_NAME, null);
        Assert.assertNull(pair);

        Command command = EnhancedRandom.random(Command.class);
        pair = manager.toFailTask(Command.TASK_NAME, command);

        Assert.assertNotNull(pair);
        Assert.assertNotNull(pair.getKey());
        Assert.assertEquals(command.getId(), pair.getKey());

        Assert.assertNotNull(pair.getValue());
        Assert.assertEquals(command.getWorkerId(), pair.getValue().getWorkerId());
        Assert.assertEquals(command.getErrorMessage(), pair.getValue().getErrorMessage());
        Assert.assertEquals(command.getRetries().intValue(), pair.getValue().getRetries());
        Assert.assertEquals(command.getRetryTimeout(), pair.getValue().getRetryTimeout());
    }

    private static void testFields(Consumer<String> consumer) {
        JavaUtils.callWithoutCheckedException(() -> {
            ClassPath.from(ExternalTaskManagerImpl.class.getClassLoader())
                    .getTopLevelClassesRecursive("com.ae.bpm.dispatcher.api.annotation.task")
                    .forEach(classInfo -> consumer.accept(StringUtils.uncapitalize(classInfo.getSimpleName())));
            return null;
        });
    }
}
