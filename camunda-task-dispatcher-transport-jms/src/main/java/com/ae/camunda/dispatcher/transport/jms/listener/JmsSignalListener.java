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

package com.ae.camunda.dispatcher.transport.jms.listener;

import com.ae.camunda.dispatcher.api.manager.SignalManager;
import com.ae.camunda.dispatcher.api.mapper.SignalMapper;
import com.ae.camunda.dispatcher.api.service.SignalRestService;
import com.ae.camunda.dispatcher.exception.CamundaRestException;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.google.common.collect.ImmutableSet;
import org.apache.activemq.ScheduledMessage;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.rest.dto.SignalDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.jms.*;
import java.util.*;
import java.util.stream.StreamSupport;

@Component
public class JmsSignalListener implements MessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(JmsSignalListener.class);

    @Autowired
    private SignalRestService signalRestService;

    @Autowired
    private SignalManager signalManager;

    @Autowired
    private SignalMapper signalMapper;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    @Qualifier("signalQueue")
    private ActiveMQQueue signalQueue;

    @Value("${camunda.dispatcher.jms.signal.type-header}")
    private String typeHeader;

    @Value("${camunda.dispatcher.jms.signal.retry-header:}")
    private String retryHeader;

    @Value("${camunda.dispatcher.jms.signal.retry-number:0}")
    private int retryNumber;

    @Value("${camunda.dispatcher.jms.signal.delay-period:0}")
    private long delayPeriod;

    @Value("${camunda.dispatcher.jms.signal.error-header}")
    private String errorHeader;

    @Value("${camunda.dispatcher.jms.signal.dl-queue:dispatcherSignalDLQ}")
    private String dlq;

    @Override
    public void onMessage(Message message) {
        if (!(message instanceof TextMessage)) {
            LOG.warn("Only text messages allowed, skipping message: {}", message);
            return;
        }

        TextMessage textMessage = (TextMessage) message;
        try {
            LOG.trace("Received message: {}", textMessage.getText());

            String signalName = textMessage.getStringProperty(typeHeader);
            LOG.debug("Received message for signal: {}", signalName);

            Object signal = signalMapper.map(textMessage.getText(), signalManager.getSignalClass(signalName));

            final Pair<String, SignalDto> signalPair = signalManager.toSignal(signalName, signal);

            try {
                signalRestService.fire(signalPair.getLeft(), signalPair.getRight());
            } catch (CamundaRestException e) {
                LOG.warn("Can't fire signal, cause: {}", e.getMessage());

                if (StringUtils.hasText(retryHeader)
                        && retryNumber > 0) {

                    int[] retryProperty = new int[]{0};
                    if (contains(textMessage.getPropertyNames(), retryHeader)) {
                        retryProperty[0] = textMessage.getIntProperty(retryHeader);
                    }

                    if (retryProperty[0] > retryNumber) {
                        throw e;
                    } else {
                        LOG.debug("Retrying signal, try #{}", retryProperty);

                        jmsTemplate.send(signalQueue.getQueueName(), (Session session) -> {
                            TextMessage retryMessage = session.createTextMessage(textMessage.getText());

                            copyHeaders(textMessage, retryMessage, ImmutableSet.of(ScheduledMessage.AMQ_SCHEDULED_DELAY, ScheduledMessage.AMQ_SCHEDULED_ID, retryHeader));

                            if (delayPeriod > 0) {
                                retryMessage.setLongProperty(ScheduledMessage.AMQ_SCHEDULED_DELAY, delayPeriod);
                            }
                            retryMessage.setIntProperty(retryHeader, retryProperty[0] + 1);

                            return retryMessage;
                        });
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Message processing error", e);
            sendToDlq(textMessage, e);
        }
    }

    private void sendToDlq(TextMessage textMessage, Throwable e) {
        jmsTemplate.send(dlq, (Session session) -> {
            TextMessage dlqMessage = session.createTextMessage(textMessage.getText());
            copyHeaders(textMessage, dlqMessage);
            dlqMessage.setStringProperty(errorHeader, e.getMessage());
            return dlqMessage;
        });
    }

    private static void copyHeaders(Message from, Message to) throws JMSException {
        copyHeaders(from, to, Collections.emptySet());
    }

    private static void copyHeaders(Message from, Message to, Set<String> exceptHeaders) throws JMSException {
        final Enumeration<String> names = (Enumeration<String>) from.getPropertyNames();
        StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(
                        new Iterator<String>() {
                            @Override
                            public boolean hasNext() {
                                return names.hasMoreElements();
                            }

                            @Override
                            public String next() {
                                return names.nextElement();
                            }
                        }
                        , Spliterator.ORDERED
                )
                , false
        ).filter(headerName -> !exceptHeaders.contains(headerName))
        .forEach(headerName ->
            JavaUtils.callWithoutCheckedException(() -> {
                to.setObjectProperty(headerName, from.getObjectProperty(headerName));
                return null;
            })
        );
    }

    private static <T> boolean contains(Enumeration<T> enumeration, T value) {
        while (enumeration.hasMoreElements()) {
            final T element = enumeration.nextElement();
            if (value.equals(element)) {
                return true;
            }
        }
        return false;
    }
}
