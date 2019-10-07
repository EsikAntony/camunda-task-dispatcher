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

package com.ae.camunda.dispatcher.api.manager;

import com.ae.camunda.dispatcher.api.annotation.CamundaSignal;
import com.google.common.base.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.rest.dto.SignalDto;

public interface SignalManager {

    static String toSignalName(Class<?> aClass) {
        CamundaSignal annotation = aClass.getAnnotation(CamundaSignal.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Class " + aClass.getName() + " must have annotation @com.ae.camunda.dispatcher.api.annotation.CamundaSignal");
        } else {
            return Strings.isNullOrEmpty(annotation.value()) ? aClass.getName() : annotation.value();
        }
    }

    Class<?> getSignalClass(String signalName);

    Pair<String, SignalDto> toSignal(String signalName, Object signal);
}
