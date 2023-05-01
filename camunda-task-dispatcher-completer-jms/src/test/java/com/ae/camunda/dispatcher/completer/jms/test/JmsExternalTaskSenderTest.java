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

package com.ae.camunda.dispatcher.completer.jms.test;

import com.ae.camunda.dispatcher.api.annotation.CamundaTask;
import com.ae.camunda.dispatcher.api.completer.Status;
import com.ae.camunda.dispatcher.api.mapper.TaskMapper;
import com.ae.camunda.dispatcher.completer.jms.JmsExternalTaskSender;
import com.ae.camunda.dispatcher.util.JavaUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.util.ReflectionUtils;

import javax.jms.JMSException;
import javax.jms.Session;
import javax.jms.TextMessage;

@RunWith(MockitoJUnitRunner.class)
public class JmsExternalTaskSenderTest {

    private static final String HEADER = "header";

    private static final String QUEUE = "queue";

    @Mock
    private JmsTemplate jmsTemplate;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private Session session;

    @Mock
    private TextMessage textMessage;

    private JmsExternalTaskSender sender;

    @Before
    public void init() throws JMSException {
        Mockito.when(session.createTextMessage()).thenReturn(textMessage);

        Mockito.doAnswer(invocation -> {
            invocation.getArgument(1, MessageCreator.class).createMessage(session);
            return null;
        }).when(jmsTemplate)
          .send(Mockito.anyString(), Mockito.any(MessageCreator.class));

        sender = new JmsExternalTaskSender();
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JmsExternalTaskSender.class, "taskMapper")
                , sender
                , taskMapper
        );
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JmsExternalTaskSender.class, "jmsTemplate")
                , sender
                , jmsTemplate
        );
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JmsExternalTaskSender.class, "header")
                , sender
                , HEADER
        );
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JmsExternalTaskSender.class, "queueIn")
                , sender
                , QUEUE
        );
    }

    @Test
    public void testSend() throws JMSException {
        sender.send(new Command(), Status.COMPLETE);

        Mockito.verify(jmsTemplate, Mockito.atLeastOnce()).send(Mockito.anyString(), Mockito.any(MessageCreator.class));
        Mockito.verify(session, Mockito.atLeastOnce()).createTextMessage();
        Mockito.verify(taskMapper, Mockito.atLeastOnce()).map(Mockito.any());

        ArgumentCaptor<String> headerValue = ArgumentCaptor.forClass(String.class);
        Mockito.verify(textMessage, Mockito.times(2)).setStringProperty(Mockito.anyString(), headerValue.capture());

        Assert.assertEquals(Command.NAME, headerValue.getAllValues().get(0));
    }

    @Test
    public void testSendWithReason() throws JMSException {
        sender.send(new Command(), Status.FAIL, "reason");

        Mockito.verify(jmsTemplate, Mockito.atLeastOnce()).send(Mockito.anyString(), Mockito.any(MessageCreator.class));
        Mockito.verify(session, Mockito.atLeastOnce()).createTextMessage();
        Mockito.verify(taskMapper, Mockito.atLeastOnce()).map(Mockito.any());

        ArgumentCaptor<String> headerValue = ArgumentCaptor.forClass(String.class);
        Mockito.verify(textMessage, Mockito.times(3)).setStringProperty(Mockito.anyString(), headerValue.capture());
        Assert.assertEquals(Command.NAME, headerValue.getAllValues().get(0));
    }

    @CamundaTask(Command.NAME)
    private static class Command {
        static final String NAME = "command";
    }
}
