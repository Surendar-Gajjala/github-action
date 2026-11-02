package com.gsr.scm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "scm.workflow")
public record WorkflowProperties(String engine, Dev dev, Camunda camunda) {

    public WorkflowProperties {
        engine = engine == null ? "dev" : engine;
        dev = dev == null ? new Dev(1000) : dev;
        camunda = camunda == null ? new Camunda(null, null, null, null, null, null, null) : camunda;
    }

    public boolean isDevEngine() {
        return "dev".equalsIgnoreCase(engine);
    }

    public record Dev(long retryBackoffMs) {
    }

    public record Camunda(String grpcAddress, String restAddress, String username, String password,
                          Duration serviceJobTimeout, Duration userTaskJobTimeout, Duration retryBackoff) {

        public Camunda {
            grpcAddress = grpcAddress == null ? "http://localhost:26500" : grpcAddress;
            restAddress = restAddress == null ? "http://localhost:8080" : restAddress;
            serviceJobTimeout = serviceJobTimeout == null ? Duration.ofMinutes(5) : serviceJobTimeout;
            userTaskJobTimeout = userTaskJobTimeout == null ? Duration.ofDays(30) : userTaskJobTimeout;
            retryBackoff = retryBackoff == null ? Duration.ofSeconds(5) : retryBackoff;
        }
    }
}
