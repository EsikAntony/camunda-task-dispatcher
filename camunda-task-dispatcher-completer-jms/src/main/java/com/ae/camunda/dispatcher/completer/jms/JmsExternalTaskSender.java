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

import com.ae.camunda.dispatcher.api.completer.Status;
import com.ae.camunda.dispatcher.api.jms.Headers;
import com.ae.camunda.dispatcher.api.manager.ExternalTaskManager;
import com.ae.camunda.dispatcher.api.mapper.TaskMapper;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.TextMessage;

@Component
public class JmsExternalTaskSender {

    private static final Logger LOG = LoggerFactory.getLogger(JmsExternalTaskSender.class);

    @Autowired
    private TaskMapper taskMapper;

    @Value("${camunda.dispatcher.jms.external-task.in-queue:dispatcherIn}")
    private String queueIn;

    @Value("${camunda.dispatcher.jms.external-task.type-header}")
    private String header;

    @Autowired
    private JmsTemplate jmsTemplate;

    public void send(Object task, Status status) {
        send(task, queueIn, status, null, null);
    }

    public void send(Object task, Status status, String reason) {
        send(task, queueIn, status, reason, null);
    }

    public void send(Object task, Status status, String reason, String detail) {
        send(task, queueIn, status, reason, detail);
    }

    private void send(Object task, String queue, Status status, String reason, String detail) {
        jmsTemplate.send(queue, (Session session) -> {
            TextMessage message = session.createTextMessage();
            message.setText(taskMapper.map(task));

            if (!Strings.isNullOrEmpty(header)) {
                setStringProperty(message, header, ExternalTaskManager.toTaskName(task.getClass()));
            }

            if (status != null) {
                setStringProperty(message, Headers.STATUS, status.name());
            }

            if (!Strings.isNullOrEmpty(reason)) {
                setStringProperty(message, Headers.REASON, reason);
            }

            if (!Strings.isNullOrEmpty(detail)) {
                setStringProperty(message, Headers.DETAIL, detail);
            }

            return message;
        });
    }

    private static void setStringProperty(TextMessage message, String header, String value) throws JMSException {
        LOG.debug("Setting header [{}] value [{}]", header, value);
        message.setStringProperty(header, value);
    }
}
