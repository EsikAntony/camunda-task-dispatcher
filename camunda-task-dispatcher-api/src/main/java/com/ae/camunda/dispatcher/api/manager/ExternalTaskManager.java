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

package com.ae.camunda.dispatcher.api.manager;

import com.ae.camunda.dispatcher.api.annotation.CamundaTask;
import com.ae.camunda.dispatcher.api.annotation.CamundaVar;
import com.google.common.base.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.rest.dto.externaltask.CompleteExternalTaskDto;
import org.camunda.bpm.engine.rest.dto.externaltask.ExternalTaskFailureDto;
import org.camunda.bpm.engine.rest.dto.externaltask.FetchExternalTasksDto;
import org.camunda.bpm.engine.rest.dto.externaltask.LockedExternalTaskDto;

import java.lang.reflect.Field;
import java.util.List;

public interface ExternalTaskManager {
    List<FetchExternalTasksDto.FetchExternalTaskTopicDto> getExternalTaskTopics(long lockTimeout);
    Object toCommand(LockedExternalTaskDto task);

    static String toTaskName(Class<?> aClass) {
        CamundaTask annotation = aClass.getAnnotation(CamundaTask.class);
        if (annotation == null) {
            throw new IllegalArgumentException("Class " + aClass.getName() + " must have annotation com.ae.camunda.dispatcher.api.annotation.CamundaTask");
        } else {
            return Strings.isNullOrEmpty(annotation.value()) ? aClass.getName() : annotation.value();
        }
    }

    Class<?> getCommandClass(String taskName);

    Pair<String,CompleteExternalTaskDto> toCompleteTask(String taskName, Object command);

    Pair<String,ExternalTaskFailureDto> toFailTask(String taskName, Object command);

    static String getVarName(Field field, CamundaVar camundaVar) {
        return Strings.isNullOrEmpty(camundaVar.value()) ? field.getName() : camundaVar.value();
    }
}
