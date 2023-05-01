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

package com.ae.camunda.dispatcher.processor.test;

import com.ae.camunda.dispatcher.api.annotation.CamundaTask;
import com.ae.camunda.dispatcher.api.mapper.TaskMapper;
import com.ae.camunda.dispatcher.api.processor.TaskProcessor;
import com.ae.camunda.dispatcher.processor.TaskProcessorRegistryImpl;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.google.common.collect.ImmutableListMultimap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.util.ReflectionUtils;

import java.util.Arrays;
import java.util.Objects;

@RunWith(MockitoJUnitRunner.class)
public class TaskProcessorRegistryImplTest {

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private TaskProcessor taskProcessor;

    private TaskProcessorRegistryImpl registry;

    @Before
    public void init() {
        registry = new TaskProcessorRegistryImpl();

        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(TaskProcessorRegistryImpl.class, "taskMapper")
                , registry
                , taskMapper
        );
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(TaskProcessorRegistryImpl.class, "taskProcessors")
                , registry
                , Arrays.asList(new CommandTaskProcessor(), new WrongTaskProcessor())
        );
    }

    @Test
    public void testInit() {
        registry.init();

        Assert.assertEquals(1, registry.getTaskProcessorMap().size());
        Assert.assertEquals(1, registry.getTaskProcessorMap().get(Command.NAME).size());
    }

    @Test
    public void testProcess() {
        String taskName = "task";
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(TaskProcessorRegistryImpl.class, "taskProcessorMap")
                , registry
                , ImmutableListMultimap.builder()
                                       .put(taskName, Pair.of(Objects.class, taskProcessor))
                                       .build()
        );

        registry.process(taskName, "some body");

        Mockito.verify(taskMapper, Mockito.atLeastOnce()).map(Mockito.anyString(), Mockito.any());
        Mockito.verify(taskProcessor, Mockito.atLeastOnce()).process(Mockito.any());
    }

    @CamundaTask(Command.NAME)
    private static class Command {
        static final String NAME = "commnad";
    }

    private static class CommandTaskProcessor implements TaskProcessor<Command> {
        @Override
        public void process(Command task) {
        }
    }

    private static class WrongTaskProcessor implements TaskProcessor<String> {
        @Override
        public void process(String task) {
        }
    }
}
