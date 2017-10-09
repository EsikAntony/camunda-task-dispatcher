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

package com.ae.camunda.dispatcher.examples.jms_xml.processor;

import com.ae.camunda.dispatcher.api.completer.ExternalTaskCompleter;
import com.ae.camunda.dispatcher.api.processor.TaskProcessor;
import com.ae.camunda.dispatcher.examples.model.command.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SimpleTaskProcessor implements TaskProcessor<Command> {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleTaskProcessor.class);

    @Autowired
    private ExternalTaskCompleter taskCompleter;

    @Override
    public void process(Command task) {
        LOG.info("Process: {}", task);

        task.setStringVar("Another string value");
        task.setAnotherStringVar("Some another string value");

        if (Math.random() > 0.5) {
            taskCompleter.complete(task);
        } else {
            task.setErrorMessage("Some error occured");
            taskCompleter.fail(task);
        }
    }
}
