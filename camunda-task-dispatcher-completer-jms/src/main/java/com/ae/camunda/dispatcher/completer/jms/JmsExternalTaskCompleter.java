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

package com.ae.camunda.dispatcher.completer.jms;

import com.ae.camunda.dispatcher.api.annotation.task.ErrorMessage;
import com.ae.camunda.dispatcher.api.completer.ExternalTaskCompleter;
import com.ae.camunda.dispatcher.api.completer.Status;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.google.common.base.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JmsExternalTaskCompleter implements ExternalTaskCompleter {

    @Autowired
    private JmsExternalTaskSender taskSender;

    @Override
    public void complete(Object task) {
        taskSender.send(task, Status.COMPLETE);
    }

    @Override
    public void fail(Object task, String reason) {
        taskSender.send(task, Status.FAIL, reason);
    }

    @Override
    public void fail(Object task) {
        List<String> errors = new LinkedList<>();
        ReflectionUtils.doWithFields(task.getClass(), field -> {
            ErrorMessage errorMessage = field.getAnnotation(ErrorMessage.class);
            if (errorMessage != null) {
                errors.add((String) JavaUtils.getFieldWithoutCheckedException(field, task));
            }
        });

        fail(task, Strings.emptyToNull(errors.stream().collect(Collectors.joining("; "))));
    }
}
