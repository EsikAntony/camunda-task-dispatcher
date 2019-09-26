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
import com.ae.camunda.dispatcher.api.annotation.task.*;

import javax.xml.bind.annotation.XmlElement;
import java.util.Date;

@CamundaTask(Command.TASK_NAME)
public class Command {

    public static final String TASK_NAME = "simpleCommand";

    private String typeField;

    @CamundaVar
    private String stringVar;

    @CamundaVar("otherVar")
    private String anotherStringVar;

    @XmlElement(name = "activity_id")
    @ActivityId
    private String activityId;

    @XmlElement(name = "activity_instance_id")
    @ActivityInstanceId
    private String activityInstanceId;

    @ErrorMessage
    private String errorMessage;

    @ErrorDetails
    private String errorDetails;

    @ExecutionId
    private String executionId;

    @Id
    private String id;

    @LockExpirationTime
    private Date lockExpirationTime;

    @ProcessDefinitionId
    private String processDefinitionId;

    @ProcessDefinitionKey
    private String processDefinitionKey;

    @ProcessInstanceId
    private String processInstanceId;

    @Retries
    private Integer retries;

    @Suspended
    private boolean suspended;

    @WorkerId
    private String workerId;

    @TopicName
    private String topicName;

    @TenantId
    private String tenantId;

    @Priority
    private long priority;

    @RetryTimeout
    private long retryTimeout;

    @BusinessKey
    private String businessKey;

    public String getStringVar() {
        return stringVar;
    }

    public void setStringVar(String stringVar) {
        this.stringVar = stringVar;
    }

    public String getAnotherStringVar() {
        return anotherStringVar;
    }

    public void setAnotherStringVar(String anotherStringVar) {
        this.anotherStringVar = anotherStringVar;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public String getActivityInstanceId() {
        return activityInstanceId;
    }

    public void setActivityInstanceId(String activityInstanceId) {
        this.activityInstanceId = activityInstanceId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Date getLockExpirationTime() {
        return lockExpirationTime;
    }

    public void setLockExpirationTime(Date lockExpirationTime) {
        this.lockExpirationTime = lockExpirationTime;
    }

    public String getProcessDefinitionId() {
        return processDefinitionId;
    }

    public void setProcessDefinitionId(String processDefinitionId) {
        this.processDefinitionId = processDefinitionId;
    }

    public String getProcessDefinitionKey() {
        return processDefinitionKey;
    }

    public void setProcessDefinitionKey(String processDefinitionKey) {
        this.processDefinitionKey = processDefinitionKey;
    }

    public String getProcessInstanceId() {
        return processInstanceId;
    }

    public void setProcessInstanceId(String processInstanceId) {
        this.processInstanceId = processInstanceId;
    }

    public Integer getRetries() {
        return retries;
    }

    public void setRetries(Integer retries) {
        this.retries = retries;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public long getPriority() {
        return priority;
    }

    public void setPriority(long priority) {
        this.priority = priority;
    }

    public String getTypeField() {
        return typeField;
    }

    public void setTypeField(String typeField) {
        this.typeField = typeField;
    }

    public long getRetryTimeout() {
        return retryTimeout;
    }

    public void setRetryTimeout(long retryTimeout) {
        this.retryTimeout = retryTimeout;
    }

    public String getErrorDetails() {
        return errorDetails;
    }

    public void setErrorDetails(String errorDetails) {
        this.errorDetails = errorDetails;
    }

    private String getBusinessKey() {
        return businessKey;
    }

    private void setBusinessKey(final String businessKey) {
        this.businessKey = businessKey;
    }
}
