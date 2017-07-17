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

import com.ae.camunda.dispatcher.api.completer.Status;
import com.ae.camunda.dispatcher.api.jms.Headers;
import com.ae.camunda.dispatcher.api.manager.ExternalTaskManager;
import com.ae.camunda.dispatcher.api.mapper.TaskMapper;
import com.ae.camunda.dispatcher.api.service.ExternalTaskRestService;
import com.ae.camunda.dispatcher.exception.CamundaRestException;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.rest.dto.externaltask.CompleteExternalTaskDto;
import org.camunda.bpm.engine.rest.dto.externaltask.ExternalTaskFailureDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import javax.jms.*;
import java.util.NoSuchElementException;

@Component
public class JmsExternalCommandListener implements MessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(JmsExternalCommandListener.class);

    @Autowired
    private ExternalTaskRestService taskService;

    @Autowired
    private ExternalTaskManager taskManager;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Value("${camunda.dispatcher.jms.type-header}")
    private String typeHeader;

    @Value("${camunda.dispatcher.jms.error-header}")
    private String errorHeader;

    @Value("${camunda.dispatcher.jms.dl-queue:dispatcherDLQ}")
    private String dlq;

    public void onMessage(Message message) {
        if (!(message instanceof TextMessage)) {
            LOG.warn("Only text messages allowed, skipping message: {}", message);
            return;
        }

        TextMessage textMessage = (TextMessage) message;
        try {
            LOG.trace("Received message: {}", textMessage.getText());

            String taskName = textMessage.getStringProperty(typeHeader);
            LOG.debug("Received message for task: {}", taskName);
            Object command = taskMapper.map(textMessage.getText(), taskManager.getCommandClass(taskName));

            String reason = textMessage.getStringProperty(Headers.REASON);
            Status status = Status.valueOf(textMessage.getStringProperty(Headers.STATUS));
            LOG.debug("Received message in status [{}] with reason [{}]", status, reason);

            switch (status) {
                case COMPLETE:
                    Pair<String, CompleteExternalTaskDto> comleteTaskPair = taskManager.toCompleteTask(taskName, command);
                    taskService.complete(comleteTaskPair.getKey(), comleteTaskPair.getValue());
                    break;

                case FAIL:
                    Pair<String, ExternalTaskFailureDto> failTaskPair = taskManager.toFailTask(taskName, command);
                    taskService.fail(failTaskPair.getKey(), failTaskPair.getValue());
                    break;
            }
        } catch (Exception e) {
            LOG.error("Message processing error", e);
            sendToDlq(textMessage, e);
        }
    }

    private void sendToDlq(TextMessage textMessage, Throwable e) {
        jmsTemplate.send(dlq, (Session session) -> {
            TextMessage dlqMessage = session.createTextMessage();
            dlqMessage.setStringProperty(errorHeader, e.getMessage());
            dlqMessage.setText(textMessage.getText());
            return dlqMessage;
        });
    }
}
