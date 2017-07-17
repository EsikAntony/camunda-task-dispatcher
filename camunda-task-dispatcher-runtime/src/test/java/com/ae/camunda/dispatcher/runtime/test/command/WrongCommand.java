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

package com.ae.camunda.dispatcher.runtime.test.command;

import com.ae.camunda.dispatcher.api.annotation.CamundaTask;
import com.ae.camunda.dispatcher.api.annotation.CamundaVar;

@CamundaTask(WrongCommand.TASK_NAME)
public class WrongCommand {

    public static final String TASK_NAME = "wrongCommand";

    @CamundaVar("VAR")
    private String var;

    public String getVar() {
        return var;
    }

    public void setVar(String var) {
        this.var = var;
    }
}
