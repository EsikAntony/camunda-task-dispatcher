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

package com.ae.camunda.dispatcher.mapper.json;


import com.ae.camunda.dispatcher.api.mapper.TaskMapper;
import com.ae.camunda.dispatcher.exception.CamundaMappingException;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;

@Component
public class JsonTaskMapper implements TaskMapper {

    private static final Logger LOG = LoggerFactory.getLogger(JsonTaskMapper.class);

    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        objectMapper = new ObjectMapper()
                .setAnnotationIntrospector(
                        new AnnotationIntrospectorPair(
                                new JaxbAnnotationIntrospector(TypeFactory.defaultInstance())
                                , new JacksonAnnotationIntrospector()
                        )
                )
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String map(Object task) {
        Class<?> commandClass = task.getClass();
        LOG.debug("Mapping class: {}", commandClass.getName());
        LOG.trace("Mapping object: {}", task);

        String json = JavaUtils.callWithoutCheckedException(() -> objectMapper.writeValueAsString(task));
        LOG.trace("JSON: {}", json);

        return json;
    }

    @Override
    public Object map(String body, Class<?> clazz) {
        try {
            return objectMapper.readValue(body, clazz);
        } catch (IOException e) {
            throw new CamundaMappingException(e);
        }
    }
}
