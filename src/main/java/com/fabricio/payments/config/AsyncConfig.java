package com.fabricio.payments.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "paymentTaskExecutor")
    public Executor paymentTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("payment-async-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(64);
        executor.setTaskTerminationTimeout(30_000L);
        executor.setCancelRemainingTasksOnClose(false);
        return executor;
    }
}
