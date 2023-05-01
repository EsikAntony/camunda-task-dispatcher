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

package com.ae.camunda.dispatcher.mapper.json.test;

import com.ae.camunda.dispatcher.mapper.json.JsonTaskMapper;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;

@RunWith(MockitoJUnitRunner.class)
public class JsonTaskMapperTest {

    private static final String JSON = "{}";

    private static final SimpleCommand COMMAND = new SimpleCommand();

    @Mock
    private ObjectMapper objectMapper;

    private JsonTaskMapper mapper;

    @Before
    public void init() throws IOException {
        Mockito.when(objectMapper.writeValueAsString(Mockito.any())).thenReturn(JSON);
        Mockito.when(objectMapper.readValue(Mockito.anyString(), Mockito.<Class<SimpleCommand>>any())).thenReturn(COMMAND);

        mapper = new JsonTaskMapper();

        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JsonTaskMapper.class, "objectMapper")
                , mapper
                , objectMapper
        );
    }

    @Test
    public void testMapCommand() throws JsonProcessingException {
        String json = mapper.map(new SimpleCommand());

        Assert.assertNotNull(json);
        Assert.assertEquals(JSON, json);

        Mockito.verify(objectMapper, Mockito.times(1)).writeValueAsString(Mockito.any());
    }

    @Test
    public void testMapJson() throws IOException {
        Object command = mapper.map(JSON, SimpleCommand.class);

        Assert.assertNotNull(command);
        Assert.assertEquals(COMMAND, command);

        Mockito.verify(objectMapper, Mockito.times(1)).readValue(Mockito.anyString(), Mockito.<Class<SimpleCommand>>any());
    }

    static class SimpleCommand {

    }
}
