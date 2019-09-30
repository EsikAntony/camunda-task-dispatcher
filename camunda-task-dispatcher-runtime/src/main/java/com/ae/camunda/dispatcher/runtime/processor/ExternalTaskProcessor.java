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

package com.ae.camunda.dispatcher.runtime.processor;

import com.ae.camunda.dispatcher.api.sender.ExternalTaskSender;
import com.ae.camunda.dispatcher.api.service.ExternalTaskRestService;
import com.ae.camunda.dispatcher.api.manager.ExternalTaskManager;
import com.ae.camunda.dispatcher.exception.CamundaRestException;
import org.camunda.bpm.engine.rest.dto.externaltask.FetchExternalTasksDto;
import com.ae.camunda.dispatcher.util.JavaUtils;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.camunda.bpm.engine.rest.dto.externaltask.ExternalTaskFailureDto;
import org.camunda.bpm.engine.rest.dto.externaltask.LockedExternalTaskDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class ExternalTaskProcessor implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ExternalTaskProcessor.class);

    @Autowired
    private ExternalTaskRestService externalTaskService;

    @Autowired
    private ExternalTaskManager taskManager;

    @Autowired
    private ExternalTaskSender taskSender;

    @Value("${camunda.dispatcher.runtime.external-task.batch:100}")
    private int taskBatchSize = 100;

    @Value("${camunda.dispatcher.runtime.external-task.worker-id:externalTaskProcessor}")
    private String workerId = "externalTaskProcessor";

    @Value("${camunda.dispatcher.runtime.external-task.concurrent-workers:2}")
    private int concurrentWorkers = 2;

    @Value("${camunda.dispatcher.runtime.external-task.empty-wait:5000}")
    private long emptyWait = 5000;

    @Value("${camunda.dispatcher.runtime.external-task.lock-timeout:86400000}")
    private long lockTimeout = 86400000;

    @Value("${camunda.dispatcher.runtime.external-task.consume-wait:5000}")
    private long consumeWait = 5000;

    @Value("${camunda.dispatcher.runtime.external-task.consume-retry:10}")
    private int consumeRetry = 10;

    private ExecutorService threadPool;

    private List<FetchExternalTasksDto.FetchExternalTaskTopicDto> topics;

    @PostConstruct
    public void init() throws IOException {
        initTopics();

        threadPool = Executors.newFixedThreadPool(concurrentWorkers, new ThreadFactoryBuilder().setNameFormat(workerId + "-%d").build());
        for(int i = 0; i < concurrentWorkers; ++i) {
            threadPool.submit(this);
        }
        threadPool.shutdown();
    }

    private void initTopics() {
        if (topics == null) {
            topics = taskManager.getExternalTaskTopics(lockTimeout);

            LOG.info("Loaded [{}] Camunda task(-s)", topics.size());
            LOG.debug("Topics: {}", topics);
        }
    }

    @PreDestroy
    public void destroy() throws InterruptedException {
        LOG.info("Stopping external task processor threads");
        threadPool.shutdownNow();
        threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        try {
            runUnsafe();
        } catch (Exception e) {
            LOG.error("External task processor thread [{}] stopped cause error", Thread.currentThread().getName(), e);
        }
    }

    private void runUnsafe() {
        LOG.info("External task processor thread [{}] started", Thread.currentThread().getName());
        while (!Thread.currentThread().isInterrupted()) {
            // запрашиваем таски со всех топиков
            List<LockedExternalTaskDto> tasks = null;
            try {
                FetchExternalTasksDto tasksDto = new FetchExternalTasksDto();
                tasksDto.setMaxTasks(taskBatchSize);
                tasksDto.setTopics(topics);
                tasksDto.setWorkerId(workerId);
                tasks = externalTaskService.fetchAndLock(tasksDto);
            } catch (CamundaRestException cre){
                LOG.warn("Error while fetching external tasks",cre);
                // оставляем список пустой. пусть отваливается дальше
            }

            if (!CollectionUtils.isEmpty(tasks)) {
                LOG.debug("Fetched [{}] task(-s)", tasks.size());
                LOG.debug("Tasks: {}", tasks);
            }

            if (CollectionUtils.isEmpty(tasks)) {
                try {
                    LOG.debug("No tasks at topics, let's wait for {}ms", emptyWait);
                    Thread.sleep(emptyWait);
                } catch (InterruptedException e) {
                    LOG.warn("Empty external tasks wait is interrupted, cause: {}", e.getMessage());
                    Thread.currentThread().interrupt();
                }
            } else {
                tasks.forEach(task -> {
                    try {
                        Object command = taskManager.toCommand(task);

                        LOG.debug("Read task: {}", command);
                        taskSender.send(command);
                    } catch (RuntimeException e) {
                        LOG.error("Task [{}] execution failed", task, e);

                        ExternalTaskFailureDto dto = new ExternalTaskFailureDto();
                        dto.setWorkerId(workerId);
                        dto.setErrorMessage(e.getMessage());

                        JavaUtils.tryConsume(
                                s -> externalTaskService.fail(task.getId(), s)
                                , dto
                                , consumeWait
                                , consumeRetry
                        );
                    }
                });
            }
        }
        LOG.info("External task processor thread [{}] stopped", Thread.currentThread().getName());
    }
}
