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

package com.ae.camunda.dispatcher.transport.jms.listener;

import com.ae.camunda.dispatcher.api.manager.ExternalTaskManager;
import com.ae.camunda.dispatcher.api.mapper.TaskMapper;
import com.ae.camunda.dispatcher.api.sender.ExternalTaskSender;
import com.ae.camunda.dispatcher.util.jms.JmsTemplate;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.TextMessage;

@Component
public class JmsExternalTaskSender implements ExternalTaskSender {

    private static final Logger LOG = LoggerFactory.getLogger(JmsExternalTaskSender.class);

    @Autowired
    private TaskMapper taskMapper;

    @Value("${camunda.dispatcher.jms.external-task.out-queue:dispatcherOut}")
    private String queueOut;

    @Value("${camunda.dispatcher.jms.external-task.type-header}")
    private String header;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Override
    public void send(Object command) {
        send(command, queueOut);
    }

    private void send(Object command, String queue) {
        jmsTemplate.send(queue, (Session session) -> {
            TextMessage message = session.createTextMessage();
            message.setText(taskMapper.map(command));

            if (!Strings.isNullOrEmpty(header)) {
                setStringProperty(message, header, ExternalTaskManager.toTaskName(command.getClass()));
            }

            return message;
        });
    }

    private static void setStringProperty(TextMessage message, String header, String value) throws JMSException {
        LOG.debug("Setting header [{}] value [{}]", header, value);
        message.setStringProperty(header, value);
    }
}
