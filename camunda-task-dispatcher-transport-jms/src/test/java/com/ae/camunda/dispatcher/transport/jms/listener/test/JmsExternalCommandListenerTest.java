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

package com.ae.camunda.dispatcher.transport.jms.listener.test;

import com.ae.camunda.dispatcher.api.completer.Status;
import com.ae.camunda.dispatcher.api.manager.ExternalTaskManager;
import com.ae.camunda.dispatcher.api.mapper.TaskMapper;
import com.ae.camunda.dispatcher.api.service.ExternalTaskRestService;
import com.ae.camunda.dispatcher.exception.CamundaRestException;
import com.ae.camunda.dispatcher.transport.jms.listener.JmsExternalCommandListener;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.ae.camunda.dispatcher.util.jms.JmsTemplate;
import org.apache.commons.lang3.tuple.Pair;
import org.camunda.bpm.engine.rest.dto.externaltask.CompleteExternalTaskDto;
import org.camunda.bpm.engine.rest.dto.externaltask.ExternalTaskFailureDto;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.util.ReflectionUtils;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.TextMessage;

@RunWith(MockitoJUnitRunner.class)
public class JmsExternalCommandListenerTest {

    private static final String HEADER = "header";

    private static final String DLQ = "dlq";

    @Mock
    private ExternalTaskRestService taskService;

    @Mock
    private ExternalTaskManager taskManager;

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private JmsTemplate jmsTemplate;

    @Mock
    private TextMessage textMessage;

    @Mock
    private BytesMessage byteMessage;

    private JmsExternalCommandListener listener;

    @Before
    public void init() {
        listener = new JmsExternalCommandListener();

        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JmsExternalCommandListener.class, "taskService")
                , listener
                , taskService
        );
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JmsExternalCommandListener.class, "taskManager")
                , listener
                , taskManager
        );
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JmsExternalCommandListener.class, "taskMapper")
                , listener
                , taskMapper
        );
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JmsExternalCommandListener.class, "jmsTemplate")
                , listener
                , jmsTemplate
        );
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JmsExternalCommandListener.class, "typeHeader")
                , listener
                , HEADER
        );
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JmsExternalCommandListener.class, "dlq")
                , listener
                , DLQ
        );
    }

    @Test
    public void testWrongOnMessage() throws CamundaRestException {
        listener.onMessage(byteMessage);

        Mockito.verify(taskService, Mockito.never()).complete(Mockito.anyString(), Mockito.any());
        Mockito.verify(taskService, Mockito.never()).fail(Mockito.anyString(), Mockito.any());
    }

    @Test
    public void testOnMessageComplete() throws JMSException, CamundaRestException {
        testOnMessage(Status.COMPLETE.name(), 1, 0);
    }

    @Test
    public void testOnMessageFail() throws JMSException, CamundaRestException {
        testOnMessage(Status.FAIL.name(), 0, 1);
    }

    @Test
    public void testOnMessageException() throws JMSException, CamundaRestException {
        Mockito.when(textMessage.getText()).thenThrow(new JMSException("Something goes wrong"));

        listener.onMessage(textMessage);

        Mockito.verify(taskService, Mockito.never()).complete(Mockito.anyString(), Mockito.any());
        Mockito.verify(taskService, Mockito.never()).fail(Mockito.anyString(), Mockito.any());
        Mockito.verify(jmsTemplate, Mockito.times(1)).send(Mockito.anyString(), Mockito.anyLong(), Mockito.any());
    }

    private void testOnMessage(String status, int completeTimes, int failtimes) throws JMSException, CamundaRestException {
        Mockito.when(textMessage.getStringProperty(Mockito.anyString())).thenReturn(status);
        Mockito.when(textMessage.getText()).thenReturn("some text body");
        Mockito.when(taskManager.toCompleteTask(Mockito.anyString(), Mockito.any())).thenReturn(Pair.of("taskId", new CompleteExternalTaskDto()));
        Mockito.when(taskManager.toFailTask(Mockito.anyString(), Mockito.any())).thenReturn(Pair.of("taskId", new ExternalTaskFailureDto()));

        listener.onMessage(textMessage);

        Mockito.verify(taskManager, Mockito.atLeastOnce()).getCommandClass(Mockito.anyString());
        Mockito.verify(taskManager, Mockito.times(completeTimes)).toCompleteTask(Mockito.anyString(), Mockito.any());
        Mockito.verify(taskManager, Mockito.times(failtimes)).toFailTask(Mockito.anyString(), Mockito.any());
        Mockito.verify(taskService, Mockito.times(completeTimes)).complete(Mockito.anyString(), Mockito.any());
        Mockito.verify(taskService, Mockito.times(failtimes)).fail(Mockito.anyString(), Mockito.any());
    }
}
