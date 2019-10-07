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

package com.ae.camunda.dispatcher.runtime.manager;

import com.ae.camunda.dispatcher.api.annotation.CamundaSignal;
import com.ae.camunda.dispatcher.api.annotation.signal.BusinessKey;
import com.ae.camunda.dispatcher.api.manager.SignalManager;
import com.ae.camunda.dispatcher.model.EntityMetadata;
import com.ae.camunda.dispatcher.runtime.manager.util.FieldUtils;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.google.common.base.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.rest.dto.SignalDto;
import org.camunda.bpm.engine.rest.dto.VariableValueDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Component
public class SignalManagerImpl implements SignalManager {

    private static final Logger LOG = LoggerFactory.getLogger(SignalManagerImpl.class);

    private static final String BUSINESS_KEY = StringUtils.uncapitalize(BusinessKey.class.getSimpleName());

    private final Map<String, EntityMetadata<?>> signalDefinitions = new HashMap<>();

    @Value("#{'${camunda.dispatcher.signal.package-name}'.split(',')}")
    private List<String> packages;

    @PostConstruct
    public void init() {
        LOG.info("Analyze packages: {}", packages);
        JavaUtils.forEachClass(packages, aClass -> {
            LOG.trace("Analyze class: {}", aClass.getName());

            CamundaSignal annotation = aClass.getAnnotation(CamundaSignal.class);
            if (annotation != null) {

                String signalName = Strings.isNullOrEmpty(annotation.value()) ? aClass.getName() : annotation.value();

                Map<String, Field> signalVars = new HashMap<>();
                ReflectionUtils.doWithFields(aClass, field -> FieldUtils.mapFieldToVar(field, signalVars));

                if (!signalVars.containsKey(BUSINESS_KEY)) {
                    LOG.warn("Class [{}] annotated with @CamundaSignal, but does not have field with @com.ae.camunda.dispatcher.api.annotation.signal.BusinessKey annotation. Has fields: {}", aClass.getName(), signalVars);
                    return;
                }

                signalDefinitions.put(signalName, new EntityMetadata<>(signalName, aClass, signalVars));
            }
        });
    }

    @Override
    public Class<?> getSignalClass(String signalName) {
        return getSignalMetadata(signalName).getClazz();
    }

    @Override
    public Pair<String, SignalDto> toSignal(String signalName, Object signal) {

        EntityMetadata<?> signalMetadata = getSignalMetadata(signalName);

        SignalDto signalDto = new SignalDto();
        signalDto.setName(signalName);

        signalDto.setVariables(
                signalMetadata.getFields()
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey
                                , entry -> {
                                    Object value = JavaUtils.getFieldWithoutCheckedException(entry.getValue(), signal);
                                    return VariableValueDto.fromTypedValue(FieldUtils.toTypedValue(value));
                                })
                        )
        );

        return Pair.of((String) signalDto.getVariables().get(BUSINESS_KEY).getValue(), signalDto);
    }

    private EntityMetadata<?> getSignalMetadata(String signalName) {
        if (!signalDefinitions.containsKey(signalName)) {
            throw new NoSuchElementException("No signal with name '" + signalName + "' found");
        }

        return signalDefinitions.get(signalName);
    }
}
