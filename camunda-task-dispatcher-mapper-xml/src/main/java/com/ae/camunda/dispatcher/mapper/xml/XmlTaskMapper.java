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

package com.ae.camunda.dispatcher.mapper.xml;

import com.ae.camunda.dispatcher.api.mapper.TaskMapper;
import com.ae.camunda.dispatcher.exception.CamundaMappingException;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;

/**
 * @author AEsik
 * Date 09.10.2017
 */
@Component
public class XmlTaskMapper implements TaskMapper {

    @Override
    public String map(Object task) {
        try {
            JAXBContext context = JAXBContextFactory.createContext(new Class[]{task.getClass()}, Collections.emptyMap());
            StringWriter sw = new StringWriter();
            context.createMarshaller().marshal(task, sw);
            return sw.toString();
        } catch (JAXBException e) {
            throw new CamundaMappingException(e);
        }
    }

    @Override
    public Object map(String body, Class<?> clazz) {
        try {
            JAXBContext context = JAXBContextFactory.createContext(new Class[]{clazz}, Collections.emptyMap());
            StringReader sr = new StringReader(body);
            return context.createUnmarshaller().unmarshal(sr);
        } catch (JAXBException e) {
            throw new CamundaMappingException(e);
        }
    }
}
