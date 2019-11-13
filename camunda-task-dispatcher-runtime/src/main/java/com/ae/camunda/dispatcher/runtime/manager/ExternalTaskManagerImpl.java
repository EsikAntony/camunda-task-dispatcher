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

package com.ae.camunda.dispatcher.runtime.manager;

import com.ae.camunda.dispatcher.api.annotation.CamundaTask;
import com.ae.camunda.dispatcher.api.annotation.task.Id;
import com.ae.camunda.dispatcher.api.annotation.task.WorkerId;
import com.ae.camunda.dispatcher.api.manager.ExternalTaskManager;
import com.ae.camunda.dispatcher.model.EntityMetadata;
import com.ae.camunda.dispatcher.runtime.manager.util.FieldUtils;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.google.common.base.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.rest.dto.VariableValueDto;
import org.camunda.bpm.engine.rest.dto.externaltask.CompleteExternalTaskDto;
import org.camunda.bpm.engine.rest.dto.externaltask.ExternalTaskFailureDto;
import org.camunda.bpm.engine.rest.dto.externaltask.FetchExternalTasksDto;
import org.camunda.bpm.engine.rest.dto.externaltask.LockedExternalTaskDto;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.type.SerializableValueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ExternalTaskManagerImpl implements ExternalTaskManager {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalTaskManagerImpl.class);

    @Value("#{'${camunda.dispatcher.command.package-name}'.split(',')}")
    private List<String> packages;

    private final Map<String, EntityMetadata<?>> externalTaskDefinitions = new HashMap<>();

    private Map<String, Class<? extends Annotation>> externalTaskFieldAnnotations;

    @PostConstruct
    public void init() {
        this.externalTaskFieldAnnotations = JavaUtils.loadAnnotations(Id.class.getPackage().getName());

        LOG.info("Analyze packages: {}", packages);
        JavaUtils.forEachClass(packages, aClass -> {
            LOG.trace("Analyze class: {}", aClass.getName());

            CamundaTask annotation = aClass.getAnnotation(CamundaTask.class);
            if (annotation != null) {
                if(!anyFieldContainsAnnotation(aClass, Id.class)
                    || !anyFieldContainsAnnotation(aClass, WorkerId.class)) {
                    return;
                }

                String taskName = Strings.isNullOrEmpty(annotation.value()) ? aClass.getName() : annotation.value();

                Map<String, Field> taskVars = new HashMap<>();
                ReflectionUtils.doWithFields(aClass, field -> FieldUtils.mapFieldToVar(field, taskVars, externalTaskFieldAnnotations));

                externalTaskDefinitions.put(taskName, new EntityMetadata<>(taskName, aClass, taskVars));
            }
        });
    }

    @Override
    public List<FetchExternalTasksDto.FetchExternalTaskTopicDto> getExternalTaskTopics(long lockTimeout) {
        return externalTaskDefinitions.values()
                .stream()
                .map(externalTask -> {
                    FetchExternalTasksDto.FetchExternalTaskTopicDto topicDto = new FetchExternalTasksDto.FetchExternalTaskTopicDto();
                    topicDto.setLockDuration(lockTimeout);
                    topicDto.setTopicName(externalTask.getName());
                    topicDto.setVariables(new ArrayList<>(externalTask.getFields().keySet()));
                    return topicDto;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Object toCommand(LockedExternalTaskDto task) {
        EntityMetadata<?> externalTask = getExternalTask(task.getTopicName());

        Object command = JavaUtils.callWithoutCheckedException(() -> externalTask.getClazz().newInstance());

        externalTask.getFields().forEach((varName, field) -> {
            if (task.getVariables() != null) {
                task.getVariables().computeIfPresent(varName, (name, value) -> {
                    final Object varValue;
                    final String serializationDataFormat = (String) value.getValueInfo().get(SerializableValueType.VALUE_INFO_SERIALIZATION_DATA_FORMAT);
                    if (Variables.SerializationDataFormats.JAVA.getName().equalsIgnoreCase(serializationDataFormat)) {
                        varValue = JavaUtils.deserialize((String) value.getValue());
                    } else {
                        varValue = value.getValue();
                    }

                    JavaUtils.setFieldWithoutCheckedException(field, command, varValue);
                    return value;
                });
            }

            Field taskField = ReflectionUtils.findField(task.getClass(), varName);
            if (taskField != null) {
                ReflectionUtils.makeAccessible(taskField);
                JavaUtils.setFieldWithoutCheckedException(field, command, () -> taskField.get(task));
            }
        });

        return command;
    }

    @Override
    public Class<?> getCommandClass(String taskName) {
        return getExternalTask(taskName).getClazz();
    }

    @Override
    public Pair<String, CompleteExternalTaskDto> toCompleteTask(String taskName, Object command) {
        if (Strings.isNullOrEmpty(taskName)
                || command == null) {
            return null;
        }

        EntityMetadata<?> externalTask = getExternalTask(taskName);

        CompleteExternalTaskDto completeTask = new CompleteExternalTaskDto();
        FieldUtils.mapVarsToFields(command, externalTask, CompleteExternalTaskDto.class, completeTask);

        completeTask.setVariables(
                externalTask.getFields()
                        .entrySet()
                        .stream()
                        .filter(entry -> !externalTaskFieldAnnotations.containsKey(entry.getKey()))
                        .collect(Collectors.toMap(
                                Map.Entry::getKey
                                , entry -> {
                                    Object value = JavaUtils.getFieldWithoutCheckedException(entry.getValue(), command);
                                    return VariableValueDto.fromTypedValue(FieldUtils.toTypedValue(value));
                                }
                        ))
        );

        return Pair.of(
                String.valueOf(JavaUtils.getFieldWithoutCheckedException(getIdField(externalTask), command))
                , completeTask
        );
    }

    @Override
    public Pair<String, ExternalTaskFailureDto> toFailTask(String taskName, Object command) {
        if (Strings.isNullOrEmpty(taskName)
                || command == null) {
            return null;
        }

        EntityMetadata<?> externalTask = getExternalTask(taskName);

        ExternalTaskFailureDto failureTask = new ExternalTaskFailureDto();
        FieldUtils.mapVarsToFields(command, externalTask, ExternalTaskFailureDto.class, failureTask);

        return Pair.of(
                String.valueOf(JavaUtils.getFieldWithoutCheckedException(getIdField(externalTask), command))
                , failureTask
        );
    }

    public Map<String, EntityMetadata<?>> getExternalTaskDefinitions() {
        return Collections.unmodifiableMap(externalTaskDefinitions);
    }

    public Map<String, Class<? extends Annotation>> getExternalTaskFieldAnnotations() {
        return Collections.unmodifiableMap(externalTaskFieldAnnotations);
    }

    private static Field getIdField(EntityMetadata<?> externalTask) {
        Field idField = externalTask.getFields().get(StringUtils.uncapitalize(Id.class.getSimpleName()));
        if (idField == null) {
            throw new NoSuchFieldError("Command '" + externalTask.getName() + "' has no field annotated with '" + Id.class.getName() + "'");
        }
        return idField;
    }

    private EntityMetadata<?> getExternalTask(String taskName) {
        if (!externalTaskDefinitions.containsKey(taskName)) {
            throw new NoSuchElementException("No task with name '" + taskName + "' found");
        }

        return externalTaskDefinitions.get(taskName);
    }

    private boolean anyFieldContainsAnnotation(Class<?> aClass, Class<? extends Annotation> annotation) {
        boolean haveAnnotation[] = new boolean[]{false};
        ReflectionUtils.doWithFields(aClass, field -> haveAnnotation[0] = true, f -> f.getAnnotation(annotation) != null);

        if (!haveAnnotation[0]) {
            LOG.warn("Class [{}] annotated with @CamundaTask, but does not have field with @{} annotation", aClass.getName(), annotation.getSimpleName());
        }

        return haveAnnotation[0];
    }
}
