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

import com.ae.camunda.dispatcher.api.manager.ExternalTaskManager;
import com.ae.camunda.dispatcher.api.sender.ExternalTaskSender;
import com.ae.camunda.dispatcher.api.service.ExternalTaskRestService;
import com.ae.camunda.dispatcher.exception.CamundaRestException;
import com.ae.camunda.dispatcher.runtime.processor.ExternalTaskProcessor;
import com.ae.camunda.dispatcher.runtime.test.command.Command;
import com.ae.camunda.dispatcher.runtime.test.dozer.VarConverter;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.google.common.collect.ImmutableMap;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.externaltask.LockedExternalTask;
import org.camunda.bpm.engine.rest.dto.externaltask.FetchExternalTasksDto;
import org.camunda.bpm.engine.rest.dto.externaltask.LockedExternalTaskDto;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.dozer.DozerBeanMapper;
import org.dozer.loader.api.BeanMappingBuilder;
import org.dozer.loader.api.FieldDefinition;
import org.dozer.loader.api.FieldsMappingOptions;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
@Deployment(resources = {"simple_diagram.bpmn"})
public class ExternalTaskProcessorTest {

    private ExternalTaskProcessor processor;

    @Mock
    private ExternalTaskSender sender;

    @Mock
    private ExternalTaskRestService externalTaskRestService;

    @Mock
    private ExternalTaskManager manager;

    private ProcessEngine processEngine;

    @Rule
    public ProcessEngineRule processEngineRule = new ProcessEngineRule("camunda.cfg.xml");

    @Before
    public void init() throws CamundaRestException, InterruptedException {
        processEngine = processEngineRule.getProcessEngine();
        processEngine.getRuntimeService()
                .startProcessInstanceByKey(
                        "Simple_Process"
                        , ImmutableMap.of("stringVar", "string", "otherVar", "other string")
                );

        Mockito.when(externalTaskRestService.fetchAndLock(Mockito.any(FetchExternalTasksDto.class))).then((invocation) -> {
            FetchExternalTasksDto arg = invocation.getArgumentAt(0, FetchExternalTasksDto.class);

            BeanMappingBuilder mappingBuilder = new BeanMappingBuilder() {
                @Override
                protected void configure() {
                    mapping(LockedExternalTask.class, LockedExternalTaskDto.class)
                            .fields("variables"
                                    , new FieldDefinition("variables").accessible()
                                    , FieldsMappingOptions.customConverter(VarConverter.class));
                }
            };
            DozerBeanMapper mapper = new DozerBeanMapper();
            mapper.addMapping(mappingBuilder);

            return arg.getTopics()
                    .stream()
                    .flatMap(topic -> processEngine.getExternalTaskService()
                                                    .fetchAndLock(arg.getMaxTasks(), arg.getWorkerId())
                                                    .topic(topic.getTopicName(), topic.getLockDuration())
                                                    .variables(new ArrayList<>(topic.getVariables()))
                                                    .execute()
                                                    .stream()
                                                    .map(task -> mapper.map(task, LockedExternalTaskDto.class))
                    )
                    .collect(Collectors.toList());
        });

        FetchExternalTasksDto.FetchExternalTaskTopicDto topicDto = new FetchExternalTasksDto.FetchExternalTaskTopicDto();
        topicDto.setLockDuration(10000);
        topicDto.setTopicName("simpleCommand");
        topicDto.setVariables(Arrays.asList("stringVar", "otherVar"));

        Mockito.when(manager.getExternalTaskTopics(Mockito.anyLong())).thenReturn(Collections.singletonList(topicDto));

        Mockito.when(manager.toCommand(Mockito.any())).thenReturn(new Command());

        processor = new ExternalTaskProcessor();

        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(ExternalTaskProcessor.class, "externalTaskService")
                , processor
                , externalTaskRestService
        );

        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(ExternalTaskProcessor.class, "taskManager")
                , processor
                , manager
        );

        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(ExternalTaskProcessor.class, "taskSender")
                , processor
                , sender
        );
    }

    @Test
    public void test() throws InterruptedException, IOException, CamundaRestException {
        processor.init();

        Thread.sleep(5000);

        ArgumentCaptor<Object> argument = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(sender, Mockito.atLeastOnce()).send(argument.capture());

        Object object = argument.getAllValues().get(0);
        Assert.assertNotNull(object);
        Assert.assertTrue(object instanceof Command);

        processor.destroy();

        Mockito.verify(externalTaskRestService, Mockito.atLeastOnce())
                .fetchAndLock(Mockito.any(FetchExternalTasksDto.class));
    }
}