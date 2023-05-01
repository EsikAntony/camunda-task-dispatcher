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

package com.ae.camunda.dispatcher.runtime.test;

import com.ae.camunda.dispatcher.exception.CamundaRestException;
import com.ae.camunda.dispatcher.runtime.service.ExternalTaskRestServiceImpl;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.camunda.bpm.engine.rest.dto.externaltask.CompleteExternalTaskDto;
import org.camunda.bpm.engine.rest.dto.externaltask.ExternalTaskFailureDto;
import org.camunda.bpm.engine.rest.dto.externaltask.FetchExternalTasksDto;
import org.camunda.bpm.engine.rest.dto.externaltask.LockedExternalTaskDto;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringBufferInputStream;
import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class ExternalTaskRestServiceImplTest {

    private ExternalTaskRestServiceImpl service;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TypeFactory typeFactory;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse response;

    @Mock
    private StatusLine statusLine;

    @Mock
    private HttpEntity httpEntity;

    @Before
    public void test() throws IOException {
        Mockito.when(objectMapper.writeValueAsString(Mockito.any())).thenReturn("JSON");
        Mockito.when(objectMapper.getTypeFactory()).thenReturn(typeFactory);
        Mockito.when(objectMapper.readValue(Mockito.<InputStream>any(), Mockito.<JavaType>any())).thenReturn(Collections.singletonList(new LockedExternalTaskDto()));

        Mockito.when(httpClient.execute(Mockito.any())).thenReturn(response);

        Mockito.when(response.getEntity()).thenReturn(httpEntity);
        Mockito.when(response.getStatusLine()).thenReturn(statusLine);

        Mockito.when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);

        Mockito.when(httpEntity.getContent()).thenReturn(new StringBufferInputStream("http response"));

        service = new ExternalTaskRestServiceImpl();

        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(ExternalTaskRestServiceImpl.class, "objectMapper")
                , service
                , objectMapper
        );
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(ExternalTaskRestServiceImpl.class, "httpClient")
                , service
                , httpClient
        );
        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(ExternalTaskRestServiceImpl.class, "engineUrl")
                , service
                , "someurl"
        );
    }

    @Test
    public void testFetchAndLock() throws CamundaRestException, IOException {
        FetchExternalTasksDto dto = new FetchExternalTasksDto();
        List<LockedExternalTaskDto> externalTasks = service.fetchAndLock(dto);

        Assert.assertNotNull(externalTasks);
        Assert.assertEquals(1, externalTasks.size());
        Assert.assertNotNull(externalTasks.get(0));

        Mockito.verify(objectMapper, Mockito.times(1)).writeValueAsString(Mockito.any());
        Mockito.verify(objectMapper, Mockito.times(1)).getTypeFactory();
        Mockito.verify(objectMapper, Mockito.times(1)).readValue(Mockito.<InputStream>any(), Mockito.<JavaType>any());
        Mockito.verify(httpClient, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    public void testComplete() throws CamundaRestException, IOException {
        service.complete("id", new CompleteExternalTaskDto());

        Mockito.verify(objectMapper, Mockito.times(1)).writeValueAsString(Mockito.any());
        Mockito.verify(httpClient, Mockito.times(1)).execute(Mockito.any());
    }

    @Test
    public void testFail() throws CamundaRestException, IOException {
        service.fail("id", new ExternalTaskFailureDto());

        Mockito.verify(objectMapper, Mockito.times(1)).writeValueAsString(Mockito.any());
        Mockito.verify(httpClient, Mockito.times(1)).execute(Mockito.any());
    }
}
