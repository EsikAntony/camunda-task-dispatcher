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
import com.ae.camunda.dispatcher.api.annotation.CamundaVar;
import com.ae.camunda.dispatcher.api.annotation.task.Id;
import com.ae.camunda.dispatcher.api.manager.ExternalTaskManager;
import com.ae.camunda.dispatcher.model.ExternalTask;
import com.ae.camunda.dispatcher.runtime.processor.ExternalTaskProcessor;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.google.common.base.Strings;
import com.google.common.reflect.ClassPath;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.rest.dto.VariableValueDto;
import org.camunda.bpm.engine.rest.dto.externaltask.CompleteExternalTaskDto;
import org.camunda.bpm.engine.rest.dto.externaltask.ExternalTaskFailureDto;
import org.camunda.bpm.engine.rest.dto.externaltask.FetchExternalTasksDto;
import org.camunda.bpm.engine.rest.dto.externaltask.LockedExternalTaskDto;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.TypedValue;
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

    private final Map<String, ExternalTask<?>> externalTaskDefinitions = new HashMap<>();

    private final Map<Class<? extends Annotation>, String> externalTaskFieldAnnotations = new HashMap<>();

    @PostConstruct
    public void init() {
        LOG.info("Analyze packages: {}", packages);
        packages.forEach(packageName ->
                JavaUtils.callWithoutCheckedException(() -> {
                    ClassPath.from(ExternalTaskProcessor.class.getClassLoader())
                            .getTopLevelClassesRecursive(packageName)
                            .forEach(classInfo -> {
                                LOG.trace("Analyze class: {}", classInfo.getName());
                                Class<?> aClass = classInfo.load();

                                CamundaTask annotation = aClass.getAnnotation(CamundaTask.class);
                                if (annotation != null) {
                                    boolean haveIdField[] = new boolean[] {false};
                                    ReflectionUtils.doWithFields(aClass, field -> haveIdField[0] = true, f -> f.getAnnotation(Id.class) != null);

                                    if (!haveIdField[0]) {
                                        LOG.warn("Class [{}] annotated with @CamundaTask, but does not have field with @Id annotation", aClass.getName());
                                        return;
                                    }

                                    String taskName = Strings.isNullOrEmpty(annotation.value()) ? aClass.getName() : annotation.value();

                                    Map<String, Field> taskVars = new HashMap<>();
                                    ReflectionUtils.doWithFields(aClass, field -> mapFieldToVar(field, taskVars));

                                    externalTaskDefinitions.put(taskName, new ExternalTask<>(taskName, aClass, taskVars));
                                }
                            });

                    return null;
                })
        );
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
        ExternalTask<?> externalTask = getExternalTask(task.getTopicName());

        Object command = JavaUtils.callWithoutCheckedException(() -> externalTask.getClazz().newInstance());

        externalTask.getFields().forEach((varName, field) -> {
            if (task.getVariables() != null) {
                task.getVariables().computeIfPresent(varName, (name, value) -> {
                    JavaUtils.setFieldWithoutCheckedException(field, command, value.getValue());
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

        ExternalTask<?> externalTask = getExternalTask(taskName);

        CompleteExternalTaskDto completeTask = new CompleteExternalTaskDto();
        mapVarsToFields(command, externalTask, CompleteExternalTaskDto.class, completeTask);

        completeTask.setVariables(
                externalTask.getFields()
                        .entrySet()
                        .stream()
                        .filter(entry -> !externalTaskFieldAnnotations.containsValue(entry.getKey()))
                        .collect(Collectors.toMap(
                                Map.Entry::getKey
                                , entry -> {
                                    Object value = JavaUtils.getFieldWithoutCheckedException(entry.getValue(), command);
                                    return VariableValueDto.fromTypedValue(toTypedValue(value));
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

        ExternalTask<?> externalTask = getExternalTask(taskName);

        ExternalTaskFailureDto failureTask = new ExternalTaskFailureDto();
        mapVarsToFields(command, externalTask, ExternalTaskFailureDto.class, failureTask);

        return Pair.of(
                String.valueOf(JavaUtils.getFieldWithoutCheckedException(getIdField(externalTask), command))
                , failureTask
        );
    }

    public Map<String, ExternalTask<?>> getExternalTaskDefinitions() {
        return Collections.unmodifiableMap(externalTaskDefinitions);
    }

    public Map<Class<? extends Annotation>, String> getExternalTaskFieldAnnotations() {
        return Collections.unmodifiableMap(externalTaskFieldAnnotations);
    }

    private static Field getIdField(ExternalTask<?> externalTask) {
        Field idField = externalTask.getFields().get(StringUtils.uncapitalize(Id.class.getSimpleName()));
        if (idField == null) {
            throw new NoSuchFieldError("Command '" + externalTask.getName() + "' has no field annotated with '" + Id.class.getName() + "'");
        }
        return idField;
    }

    private static TypedValue toTypedValue(Object value) {
        if (value instanceof Integer) {
            return Variables.integerValue((Integer) value);
        } else if (value instanceof String) {
            return Variables.stringValue((String) value);
        } else if (value instanceof Boolean) {
            return Variables.booleanValue((Boolean) value);
        } else if (value instanceof byte[]) {
            return Variables.byteArrayValue((byte[]) value);
        } else if (value instanceof Date) {
            return Variables.dateValue((Date) value);
        } else if (value instanceof Long) {
            return Variables.longValue((Long) value);
        } else if (value instanceof Short) {
            return Variables.shortValue((Short) value);
        } else if (value instanceof Double) {
            return Variables.doubleValue((Double) value);
        } else if (value instanceof Number) {
            return Variables.numberValue((Number) value);
        }
        return Variables.untypedValue(value);
    }

    private <T> void mapVarsToFields(Object command, ExternalTask<?> externalTask, Class<T> taskClass, T task) {
        ReflectionUtils.doWithFields(taskClass, field -> {
            if (!externalTask.getFields().containsKey(field.getName())) {
                return;
            }

            Field commandField = externalTask.getFields().get(field.getName());
            JavaUtils.setFieldWithoutCheckedException(
                    field
                    , task
                    , () -> JavaUtils.getFieldWithoutCheckedException(commandField, command)
            );
        });
    }

    private ExternalTask<?> getExternalTask(String taskName) {
        if (!externalTaskDefinitions.containsKey(taskName)) {
            throw new NoSuchElementException("No task with name '" + taskName + "' found");
        }

        return externalTaskDefinitions.get(taskName);
    }

    private void mapFieldToVar(Field field, Map<String, Field> taskVars) {
        CamundaVar camundaVar = field.getAnnotation(CamundaVar.class);
        if (camundaVar != null) {
            taskVars.put(
                    ExternalTaskManager.getVarName(field, camundaVar)
                    , field
            );
        }

        loadExternalTaskFieldAnnotationsIfNeeded();

        externalTaskFieldAnnotations.forEach((annotation, name) -> mapTaskFieldToVar(annotation, field, taskVars));
    }

    private void loadExternalTaskFieldAnnotationsIfNeeded() {
        if (externalTaskFieldAnnotations.isEmpty()) {
            JavaUtils.callWithoutCheckedException(() -> {
                ClassPath.from(ExternalTaskProcessor.class.getClassLoader())
                        .getTopLevelClassesRecursive(Id.class.getPackage().getName())
                        .forEach(classInfo -> {
                            Class<? extends Annotation> annotationClass = (Class<? extends Annotation>) classInfo.load();
                            externalTaskFieldAnnotations.put(annotationClass, StringUtils.uncapitalize(annotationClass.getSimpleName()));
                        });
                return null;
            });
        }
    }

    private <T extends Annotation> void mapTaskFieldToVar(Class<T> annotationClass, Field field, Map<String, Field> taskVars) {
        T annotation = field.getAnnotation(annotationClass);
        if (annotation != null) {
            taskVars.put(
                    StringUtils.uncapitalize(annotationClass.getSimpleName())
                    , field
            );
        }
    }
}
