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

package com.ae.camunda.dispatcher.model;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;

public class EntityMetadata<T> {

    private String name;

    private Class<T> clazz;

    private Map<String, Field> fields;

    public EntityMetadata(String name, Class<T> clazz, Map<String, Field> fields) {
        this.name = name;
        this.clazz = clazz;
        this.fields = fields;
    }

    public String getName() {
        return name;
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public Map<String, Field> getFields() {
        return Collections.unmodifiableMap(fields);
    }
}
