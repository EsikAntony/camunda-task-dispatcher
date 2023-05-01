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

package com.ae.camunda.dispatcher.completer.jms.test;

import com.ae.camunda.dispatcher.api.annotation.task.ErrorMessage;
import com.ae.camunda.dispatcher.api.completer.Status;
import com.ae.camunda.dispatcher.completer.jms.JmsExternalTaskCompleter;
import com.ae.camunda.dispatcher.completer.jms.JmsExternalTaskSender;
import com.ae.camunda.dispatcher.util.JavaUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.util.ReflectionUtils;

@RunWith(MockitoJUnitRunner.class)
public class JmsExternalTaskCompleterTest {

    @Mock
    private JmsExternalTaskSender sender;

    private JmsExternalTaskCompleter completer;

    @Before
    public void init() {
        completer = new JmsExternalTaskCompleter();

        JavaUtils.setFieldWithoutCheckedException(
                ReflectionUtils.findField(JmsExternalTaskCompleter.class, "taskSender")
                , completer
                , sender
        );
    }

    @Test
    public void testComplete() {
        completer.complete(null);

        ArgumentCaptor<Status> argument = ArgumentCaptor.forClass(Status.class);
        Mockito.verify(sender, Mockito.atLeastOnce()).send(Mockito.any(), argument.capture());

        Status status = argument.getAllValues().get(0);
        Assert.assertNotNull(status);
        Assert.assertEquals(Status.COMPLETE, status);
    }

    @Test
    public void testFail() {
        completer.fail(new Object());

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(sender, Mockito.atLeastOnce()).send(Mockito.any(), statusCaptor.capture(), reasonCaptor.capture(), detailCaptor.capture());

        Status status = statusCaptor.getAllValues().get(0);
        Assert.assertNotNull(status);
        Assert.assertEquals(Status.FAIL, status);

        String reason = reasonCaptor.getAllValues().get(0);
        Assert.assertNull(reason);

        String detail = detailCaptor.getAllValues().get(0);
        Assert.assertNull(detail);
    }

    @Test
    public void testFailWithReason() {
        final String error = "some error";
        final String errorDetail = "some detail";
        completer.fail(null, error, errorDetail);

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> detailCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(sender, Mockito.atLeastOnce()).send(Mockito.any(), statusCaptor.capture(), reasonCaptor.capture(), detailCaptor.capture());

        Status status = statusCaptor.getAllValues().get(0);
        Assert.assertNotNull(status);
        Assert.assertEquals(Status.FAIL, status);

        String reason = reasonCaptor.getAllValues().get(0);
        Assert.assertNotNull(reason);
        Assert.assertEquals(error, reason);

        String detail = detailCaptor.getAllValues().get(0);
        Assert.assertNotNull(detail);
        Assert.assertEquals(errorDetail, detail);
    }

    @Test
    public void testFailWithReasonField() {
        String error = "some error";
        completer.fail(new Command(error));

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(sender, Mockito.atLeastOnce()).send(Mockito.any(), statusCaptor.capture(), reasonCaptor.capture(), Mockito.any());

        Status status = statusCaptor.getAllValues().get(0);
        Assert.assertNotNull(status);
        Assert.assertEquals(Status.FAIL, status);

        String reason = reasonCaptor.getAllValues().get(0);
        Assert.assertNotNull(reason);
        Assert.assertEquals(error, reason);
    }

    private static class Command {
        @ErrorMessage
        private String error;

        Command(String error) {
            this.error = error;
        }
    }
}
