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

package com.ae.camunda.dispatcher.runtime.service;

import com.ae.camunda.dispatcher.api.service.SignalRestService;
import com.ae.camunda.dispatcher.exception.CamundaRestException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.camunda.bpm.engine.rest.dto.SignalDto;
import org.camunda.bpm.engine.rest.dto.runtime.ExecutionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SignalRestServiceImpl implements SignalRestService {

    private static final Logger LOG = LoggerFactory.getLogger(SignalRestServiceImpl.class);

    @Value("${camunda.dispatcher.runtime.engine-url}")
    private String engineUrl;

    @Value("${camunda.dispatcher.runtime.engine.user:}")
    private String camundaUser;

    @Value("${camunda.dispatcher.runtime.engine.pass:}")
    private String camundaPass;

    private ObjectMapper objectMapper;

    private CloseableHttpClient httpClient;

    @PostConstruct
    public void init() {
        objectMapper = new ObjectMapper();
        final HttpClientBuilder clientBuilder = HttpClientBuilder.create();
        if (StringUtils.hasText(camundaUser)) {
            final BasicCredentialsProvider provider = new BasicCredentialsProvider();
            provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(camundaUser, camundaPass));
            clientBuilder.setDefaultCredentialsProvider(provider);
        }
        httpClient = clientBuilder.build();
    }

    @Override
    public void fire(String businessKey, SignalDto signal) throws CamundaRestException {
        LOG.debug("Firing signal [{}] for businessKey [{}]", signal.getName(), businessKey);

        final List<String> executions = findExecutionByBusinessKeyAndSignalEventSubscriptionName(businessKey, signal.getName())
                .stream()
                .filter(executionDto -> !executionDto.isEnded())
                .map(ExecutionDto::getId)
                .collect(Collectors.toList());

        if (executions.isEmpty()) {
            throw CamundaRestException.fromCodeAndResponse(404, "There is no active execution with business key "
                    + businessKey + " subscribed to signal event: " + signal.getName());
        } else {
            LOG.debug("Found active executions for businessKey [{}]: {}", businessKey, executions);
        }

        for (String executionId : executions) {
            final SignalDto dto = new SignalDto();
            dto.setName(signal.getName());
            dto.setVariables(signal.getVariables());
            dto.setExecutionId(executionId);

            throwSignal(dto);
        }
    }

    private void throwSignal(final SignalDto dto) throws CamundaRestException {
        try {
            final HttpPost post = new HttpPost(engineUrl + "/signal");
            post.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            post.setEntity(new StringEntity(objectMapper.writeValueAsString(dto)));
            try (CloseableHttpResponse response = httpClient.execute(post)) {
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_NO_CONTENT) {
                    throw CamundaRestException.fromCodeAndResponse(response.getStatusLine().getStatusCode()
                            , EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new CamundaRestException(e);
        }
    }

    private List<ExecutionDto> findExecutionByBusinessKeyAndSignalEventSubscriptionName(String businessKey, String signalEventSubscriptionName) throws CamundaRestException {
        try {
            final URIBuilder uriBuilder = new URIBuilder(engineUrl + "/execution");
            uriBuilder.addParameter("businessKey", businessKey);
            uriBuilder.addParameter("signalEventSubscriptionName", signalEventSubscriptionName);
            final HttpGet get = new HttpGet(uriBuilder.build());
            try (CloseableHttpResponse response = httpClient.execute(get)) {
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    return objectMapper.readValue(response.getEntity().getContent(), new TypeReference<List<ExecutionDto>>() {
                    });
                } else {
                    throw CamundaRestException.fromCodeAndResponse(response.getStatusLine().getStatusCode()
                            , EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new CamundaRestException(e);
        }
    }
}
