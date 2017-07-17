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

package com.ae.camunda.dispatcher.exception;

public class CamundaRestException extends Exception {
    private int httpCode;

    private String response;

    public int getHttpCode() {
        return httpCode;
    }

    public void setHttpCode(int httpCode) {
        this.httpCode = httpCode;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public static CamundaRestException fromCodeAndResponse(int code,String response){
        CamundaRestException exception = new CamundaRestException();
        exception.setHttpCode(code);
        exception.setResponse(response);
        return exception;
    }

    @Override
    public String getMessage(){
        if (response!=null && httpCode!=0){
            return String.format("Server has responded with %1$s code. Response is [%2$s].",httpCode,response);
        } else {
            return super.getMessage();
        }
    }

    public CamundaRestException() {
    }

    public CamundaRestException(String message) {
        super(message);
    }

    public CamundaRestException(String message, Throwable cause) {
        super(message, cause);
    }

    public CamundaRestException(Throwable cause) {
        super(cause);
    }

    public CamundaRestException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
