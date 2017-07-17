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

package com.ae.camunda.dispatcher.processor;

import com.ae.camunda.dispatcher.api.annotation.CamundaTask;
import com.ae.camunda.dispatcher.api.mapper.TaskMapper;
import com.ae.camunda.dispatcher.api.processor.TaskProcessor;
import com.ae.camunda.dispatcher.api.processor.TaskProcessorRegistry;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.stream.Stream;

@Component
public class TaskProcessorRegistryImpl implements TaskProcessorRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(TaskProcessorRegistryImpl.class);

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private List<TaskProcessor> taskProcessors;

    private Multimap<String, Pair<Class<?>, TaskProcessor>> taskProcessorMap;

    @PostConstruct
    public void init() {
        ImmutableListMultimap.Builder<String, Pair<Class<?>, TaskProcessor>> builder = ImmutableListMultimap.builder();
        taskProcessors.forEach(processor ->
                Stream.of(processor.getClass().getGenericInterfaces())
                        .filter(iface -> iface instanceof ParameterizedType)
                        .map(iface -> (ParameterizedType) iface)
                        .filter(iface -> iface.getRawType() != null && TaskProcessor.class.getName().equals(iface.getRawType().getTypeName()))
                        .map(iface -> iface.getActualTypeArguments()[0].getTypeName())
                        .findFirst()
                        .ifPresent(taskClassName -> {
                            Class<?> taskClass = JavaUtils.callWithoutCheckedException(() -> Class.forName(taskClassName));
                            CamundaTask camundaTask = taskClass.getAnnotation(CamundaTask.class);
                            if (camundaTask == null) {
                                LOG.warn("Task processor [{}] processes task class [{}] without annotation [{}]"
                                        , processor.getClass().getName(), taskClassName, CamundaTask.class.getName());
                                return;
                            }

                            String taskName = camundaTask.value();
                            if (Strings.isNullOrEmpty(taskName)) {
                                taskName = taskClass.getName();
                            }
                            LOG.debug("Register processor [{}] for task [{}]", processor.getClass().getName(), taskName);
                            builder.put(taskName, Pair.of(taskClass, processor));
                        })
        );
        taskProcessorMap = builder.build();
    }

    @Override
    public void process(String taskName, String taskBody) {
        if (!taskProcessorMap.containsKey(taskName)) {
            LOG.warn("No processor bound to task: {}", taskName);
            return;
        }

        taskProcessorMap.get(taskName).forEach(pair -> {
            Object command = taskMapper.map(taskBody, pair.getKey());
            LOG.debug("Recv command: {}", command);

            pair.getValue().process(command);
        });
    }

    public Multimap<String, Pair<Class<?>, TaskProcessor>> getTaskProcessorMap() {
        return Multimaps.unmodifiableMultimap(taskProcessorMap);
    }
}
