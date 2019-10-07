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

package com.ae.camunda.dispatcher.receiver.jms;

import com.ae.camunda.dispatcher.api.processor.TaskProcessorRegistry;
import com.ae.camunda.dispatcher.util.JavaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

@Component
public class JmsExternalTaskListener implements MessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(JmsExternalTaskListener.class);

    @Value("${camunda.dispatcher.jms.external-task.type-header}")
    private String header;

    @Autowired
    private TaskProcessorRegistry registry;

    @Override
    public void onMessage(Message message) {
        LOG.debug("Recv message: {}", message);

        registry.process(
                JavaUtils.callWithoutCheckedException(() -> message.getStringProperty(header))
                , JavaUtils.callWithoutCheckedException(((TextMessage) message)::getText)
        );
    }
}