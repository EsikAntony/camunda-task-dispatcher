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

package com.ae.camunda.dispatcher.receiver.jms.test;

import com.ae.camunda.dispatcher.api.processor.TaskProcessorRegistry;
import com.ae.camunda.dispatcher.receiver.jms.JmsExternalTaskListener;
import com.ae.camunda.dispatcher.util.JavaUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.util.ReflectionUtils;

import javax.jms.JMSException;
import javax.jms.TextMessage;

@RunWith(MockitoJUnitRunner.class)
public class JmsExternalTaskListenerTest {

    @Mock
    private TaskProcessorRegistry registry;

    @Mock
    private TextMessage message;

    private JmsExternalTaskListener listener;

    @Before
    public void init() {
        listener = new JmsExternalTaskListener();

        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JmsExternalTaskListener.class, "registry")
                , listener
                , registry
        );
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JmsExternalTaskListener.class, "header")
                , listener
                , "header"
        );
    }

    @Test
    public void testOnMessage() throws JMSException {
        Mockito.when(message.getText()).thenReturn("message body");
        Mockito.when(message.getStringProperty(Mockito.anyString())).thenReturn("string property");

        listener.onMessage(message);

        Mockito.verify(registry, Mockito.atLeastOnce()).process(Mockito.anyString(), Mockito.anyString());
        Mockito.verify(message, Mockito.atLeastOnce()).getStringProperty(Mockito.anyString());
        Mockito.verify(message, Mockito.atLeastOnce()).getText();
    }
}
