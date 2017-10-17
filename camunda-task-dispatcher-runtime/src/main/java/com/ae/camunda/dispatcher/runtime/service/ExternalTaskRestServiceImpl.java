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

package com.ae.camunda.dispatcher.runtime.service;

import com.ae.camunda.dispatcher.api.service.ExternalTaskRestService;
import com.ae.camunda.dispatcher.exception.CamundaRestException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.camunda.bpm.engine.rest.dto.externaltask.CompleteExternalTaskDto;
import org.camunda.bpm.engine.rest.dto.externaltask.ExternalTaskFailureDto;
import org.camunda.bpm.engine.rest.dto.externaltask.FetchExternalTasksDto;
import org.camunda.bpm.engine.rest.dto.externaltask.LockedExternalTaskDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;

@Component
public class ExternalTaskRestServiceImpl implements ExternalTaskRestService {

    private static final String FETCH_AND_LOCK_TASKS = "fetchAndLock";

    private static final String EXTERNAL_TASK = "external-task";

    @Value("${camunda.dispatcher.runtime.engine-url}")
    private String engineUrl;

    @Value("${camunda.dispatcher.runtime.engine.user:}")
    private String camundaUser;

    @Value("${camunda.dispatcher.runtime.engine.pass:}")
    private String camundaPass;

    private ObjectMapper objectMapper;

    private HttpClient httpClient;

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
    public List<LockedExternalTaskDto> fetchAndLock(FetchExternalTasksDto fetchingDto) throws CamundaRestException {
        HttpPost post = new HttpPost(makeTaskUrl(FETCH_AND_LOCK_TASKS));
        try {
            String requestJson = objectMapper.writeValueAsString(fetchingDto);
            StringEntity entity = new StringEntity(requestJson);
            post.setEntity(entity);
            post.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            return executeAbstractMethod(post, objectMapper.getTypeFactory().constructCollectionType(LinkedList.class, LockedExternalTaskDto.class));
        } catch (CamundaRestException cre){
            throw cre;
        } catch (Exception ex){
            throw new CamundaRestException(ex);
        }
    }

    @Override
    public void complete(String taskId, CompleteExternalTaskDto dto) throws CamundaRestException {
        executeAbstractMethod(makeTaskUrl(taskId, "complete"), dto);
    }

    @Override
    public void fail(String taskId, ExternalTaskFailureDto dto) throws CamundaRestException {
        executeAbstractMethod(makeTaskUrl(taskId, "failure"), dto);
    }

    private <T> T executeAbstractMethod(HttpUriRequest request, JavaType type) throws CamundaRestException, IOException {
        return unmarshallToObject(executeMethod(request), type);
    }

    private InputStream executeMethod(HttpUriRequest request) throws CamundaRestException, IOException {
        HttpResponse response = httpClient.execute(request);
        StringWriter stringWriter = new StringWriter();
        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_NO_CONTENT) {
            throw CamundaRestException.fromCodeAndResponse(statusCode, stringWriter.toString());
        }
        return response.getEntity().getContent();
    }

    private <T> void executeAbstractMethod(String url, T data) throws CamundaRestException {
        HttpPost post = new HttpPost(url);
        try {
            String requestJson = objectMapper.writeValueAsString(data);
            StringEntity entity = new StringEntity(requestJson);
            post.setEntity(entity);
            post.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
            executeAbstractMethod(post);
        } catch (Exception ex) {
            throw new CamundaRestException(ex);
        }
    }

    private void executeAbstractMethod(HttpUriRequest request) throws CamundaRestException,IOException{
        HttpResponse response = httpClient.execute(request);
        StringWriter stringWriter = new StringWriter();
        int statusCode = response.getStatusLine().getStatusCode();
        if (response.getEntity() != null
                && response.getEntity().getContent() != null) {
            IOUtils.copy(response.getEntity().getContent(), stringWriter);
        }
        if (statusCode != HttpStatus.SC_OK && statusCode != HttpStatus.SC_NO_CONTENT) {
            throw CamundaRestException.fromCodeAndResponse(statusCode,stringWriter.toString());
        }
    }

    private <T> T unmarshallToObject(InputStream json, JavaType type) throws IOException {
        return objectMapper.readValue(json, type);
    }

    private String makeTaskUrl(String taskId, String action) {
        return engineUrl + "/" + EXTERNAL_TASK + "/" + taskId + "/" + action;
    }

    private String makeTaskUrl(String action) {
        return engineUrl + "/" + EXTERNAL_TASK + "/" + action;
    }
}

