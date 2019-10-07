/*
 * Copyright (c) 2019 Antony Esik
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

package com.ae.camunda.dispatcher.runtime.manager.util;

import com.ae.camunda.dispatcher.api.annotation.CamundaVar;
import com.ae.camunda.dispatcher.api.manager.ExternalTaskManager;
import com.ae.camunda.dispatcher.model.EntityMetadata;
import com.ae.camunda.dispatcher.util.JavaUtils;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.*;

public abstract class FieldUtils {

    public static TypedValue toTypedValue(Object value) {
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

    public static <T> void mapVarsToFields(Object entity, EntityMetadata<?> entityMetadata, Class<T> clazz, T result) {
        ReflectionUtils.doWithFields(clazz, field -> {
            if (!entityMetadata.getFields().containsKey(field.getName())) {
                return;
            }

            Field entityField = entityMetadata.getFields().get(field.getName());
            JavaUtils.setFieldWithoutCheckedException(
                    field
                    , result
                    , () -> JavaUtils.getFieldWithoutCheckedException(entityField, entity)
            );
        });
    }

    public static void mapFieldToVar(Field field, Map<String, Field> variables, Map<String, Class<? extends Annotation>> filedAnnotations) {
        CamundaVar camundaVar = field.getAnnotation(CamundaVar.class);
        if (camundaVar != null) {
            variables.put(
                    ExternalTaskManager.getVarName(field, camundaVar)
                    , field
            );
        } else {
            Arrays.stream(field.getAnnotations())
                    .map(Annotation::annotationType)
                    .map(clazz -> clazz.getAnnotation(CamundaVar.class))
                    .filter(Objects::nonNull)
                    .forEach(var -> {
                        variables.put(
                                ExternalTaskManager.getVarName(field, var)
                                , field
                        );
                    });
        }

        filedAnnotations.forEach((name, annotation) -> mapFieldToVar(annotation, field, variables));
    }

    public static void mapFieldToVar(Field field, Map<String, Field> variables) {
        mapFieldToVar(field, variables, Collections.emptyMap());
    }

    private static <T extends Annotation> void mapFieldToVar(Class<T> annotationClass, Field field, Map<String, Field> variables) {
        T annotation = field.getAnnotation(annotationClass);
        if (annotation != null) {
            variables.put(
                    StringUtils.uncapitalize(annotationClass.getSimpleName())
                    , field
            );
        }
    }
}
