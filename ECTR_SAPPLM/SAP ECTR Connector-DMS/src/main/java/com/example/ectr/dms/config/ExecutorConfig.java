// VERIFIED: Spring thread pool with MDC propagation, so log lines from SAP worker threads keep the
// caller's correlation id.
package com.example.ectr.dms.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

@Configuration
public class ExecutorConfig {

    /**
     * Bounded pool for SAP calls. A full pool and queue raise TaskRejectedException,
     * which the API turns into 503 instead of letting requests pile up.
     */
    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor sapExecutor(IntegrationProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.sap().workerThreads());
        executor.setMaxPoolSize(properties.sap().workerThreads());
        executor.setQueueCapacity(properties.sap().queueCapacity());
        executor.setThreadNamePrefix("sap-");
        executor.setTaskDecorator(runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                if (context != null) {
                    MDC.setContextMap(context);
                }
                try {
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
