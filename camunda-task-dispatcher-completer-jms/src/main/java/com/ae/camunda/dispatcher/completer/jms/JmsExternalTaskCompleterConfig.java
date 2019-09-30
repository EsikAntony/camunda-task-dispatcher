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

import com.google.common.base.Strings;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.core.JmsTemplate;

import javax.jms.ConnectionFactory;

@Configuration
public class JmsExternalTaskCompleterConfig {

    @Value("${camunda.dispatcher.jms.session.size:5}")
    private int sessionCacheSize;

    @Value("${camunda.dispatcher.jms.activemq.broker-url:tcp://localhost:61616}")
    private String brokerUrl;

    @Value("${camunda.dispatcher.jms.activemq.username:admin}")
    private String brokerUser;

    @Value("${camunda.dispatcher.jms.activemq.password:admin}")
    private String brokerPassword;

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
    public JmsTemplate jmsTemplate(ConnectionFactory connectionFactory) {
        return new JmsTemplate(connectionFactory);
    }
}
