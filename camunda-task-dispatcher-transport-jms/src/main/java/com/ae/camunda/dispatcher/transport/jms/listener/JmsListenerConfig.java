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

import com.google.common.base.Strings;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

import javax.jms.ConnectionFactory;

@Configuration
public class JmsListenerConfig {

    private static final Logger LOG = LoggerFactory.getLogger(JmsListenerConfig.class);

    @Value("${camunda.dispatcher.jms.external-task.in-queue:dispatcherIn}")
    private String inTaskQueueName;

    @Value("${camunda.dispatcher.jms.signal.in-queue:dispatcherSignalIn}")
    private String inSignalQueueName;

    @Value("${camunda.dispatcher.jms.activemq.broker-url:tcp://localhost:61616}")
    private String brokerUrl;

    @Value("${camunda.dispatcher.jms.activemq.username:admin}")
    private String brokerUser;

    @Value("${camunda.dispatcher.jms.activemq.password:admin}")
    private String brokerPassword;

    @Value("${camunda.dispatcher.jms.session.size:5}")
    private int sessionCacheSize;

    @Value("${camunda.dispatcher.jms.concurrent-consumers:1}")
    private int concurrentConsumers;

    @Value("${camunda.dispatcher.jms.max-messages-per-task:1}")
    private int maxMessagesPerTask;

    @Value("${camunda.dispatcher.jms.receive-timeout:5000}")
    private long receiveTimeout;

    @Bean
    public ActiveMQQueue taskQueue() {
        return new ActiveMQQueue(inTaskQueueName);
    }

    @Bean
    public ActiveMQQueue signalQueue() {
        return new ActiveMQQueue(inSignalQueueName);
    }

    @Bean
    public ConnectionFactory connectionFactory() {
        ActiveMQConnectionFactory activeMQConnectionFactory = new ActiveMQConnectionFactory(brokerUrl);

        if (!Strings.isNullOrEmpty(brokerUser)) {
            activeMQConnectionFactory.setUserName(brokerUser);
            activeMQConnectionFactory.setPassword(brokerPassword);
        }

        CachingConnectionFactory bean = new CachingConnectionFactory(activeMQConnectionFactory);
        bean.setSessionCacheSize(sessionCacheSize);
        return bean;
    }

    @Bean
    @Autowired
    public DefaultMessageListenerContainer taskMessageListenerContainer(JmsExternalCommandListener externalTaskListener
            , ConnectionFactory connectionFactory
            , ActiveMQQueue taskQueue) {
        DefaultMessageListenerContainer listenerContainer = new DefaultMessageListenerContainer();
        listenerContainer.setMessageListener(externalTaskListener);
        listenerContainer.setDestination(taskQueue);
        listenerContainer.setConnectionFactory(connectionFactory);

        listenerContainer.setAcceptMessagesWhileStopping(false);
        listenerContainer.setSessionTransacted(true);
        listenerContainer.setConcurrentConsumers(concurrentConsumers);
        listenerContainer.setMaxMessagesPerTask(maxMessagesPerTask);
        listenerContainer.setReceiveTimeout(receiveTimeout);
        LOG.debug("DefaultMessageListenerContainer for queue [{}] with message selector [{}] was started", listenerContainer.getDestination(), listenerContainer.getMessageSelector());
        return listenerContainer;
    }

    @Bean
    @Autowired
    public DefaultMessageListenerContainer signalMessageListenerContainer(JmsSignalListener signalListener
            , ConnectionFactory connectionFactory
            , ActiveMQQueue signalQueue) {
        DefaultMessageListenerContainer listenerContainer = new DefaultMessageListenerContainer();
        listenerContainer.setMessageListener(signalListener);
        listenerContainer.setDestination(signalQueue);
        listenerContainer.setConnectionFactory(connectionFactory);

        listenerContainer.setAcceptMessagesWhileStopping(false);
        listenerContainer.setSessionTransacted(true);
        listenerContainer.setConcurrentConsumers(concurrentConsumers);
        listenerContainer.setMaxMessagesPerTask(maxMessagesPerTask);
        listenerContainer.setReceiveTimeout(receiveTimeout);
        LOG.debug("DefaultMessageListenerContainer for queue [{}] with message selector [{}] was started", listenerContainer.getDestination(), listenerContainer.getMessageSelector());
        return listenerContainer;
    }

    @Bean
    @Autowired
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        return new JmsTemplate(connectionFactory);
    }
}
