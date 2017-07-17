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

package com.ae.camunda.dispatcher.runtime.test.dozer;

import org.camunda.bpm.engine.rest.dto.VariableValueDto;
import org.camunda.bpm.engine.variable.VariableMap;
import org.dozer.CustomConverter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class VarConverter implements CustomConverter {

    @Override
    public Object convert(Object dest, Object src, Class<?> destinationClass, Class<?> sourceClass) {
        if (dest == null) {
            dest = new HashMap<String, VariableValueDto>();
        }
        Map<String, VariableValueDto> destVars = (Map<String, VariableValueDto>) dest;

        VariableMap srcVars = (VariableMap) src;

        srcVars.forEach((varName, value) -> {
            VariableValueDto valueDto = new VariableValueDto();
            valueDto.setValue(Objects.toString(value));
            destVars.put(varName, valueDto);
        });

        return destVars;
    }
}