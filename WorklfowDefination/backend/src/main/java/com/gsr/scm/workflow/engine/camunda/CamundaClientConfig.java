package com.gsr.scm.workflow.engine.camunda;

import com.gsr.scm.config.WorkflowProperties;
import io.camunda.client.CamundaClient;
import io.camunda.client.CamundaClientBuilder;
import io.camunda.client.CredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Explicit Camunda 8 client configuration. Creating the client does not start an engine: the Orchestration Cluster
 * (e.g. Camunda 8 Run) must already be running at the configured gRPC/REST addresses.
 */
@Configuration
@ConditionalOnProperty(name = "scm.workflow.engine", havingValue = "camunda")
public class CamundaClientConfig {

    private static final Logger log = LoggerFactory.getLogger(CamundaClientConfig.class);

    @Bean(destroyMethod = "close")
    CamundaClient camundaClient(WorkflowProperties properties) {
        WorkflowProperties.Camunda cfg = properties.camunda();
        CamundaClientBuilder builder = CamundaClient.newClientBuilder()
                .grpcAddress(URI.create(cfg.grpcAddress()))
                .restAddress(URI.create(cfg.restAddress()))
                .preferRestOverGrpc(false);
        if (cfg.username() != null && !cfg.username().isBlank()) {
            CredentialsProvider credentials = CredentialsProvider.newBasicAuthCredentialsProviderBuilder()
                    .username(cfg.username())
                    .password(cfg.password())
                    .build();
            builder.credentialsProvider(credentials);
        }
        log.info("Workflow engine: CAMUNDA 8 (gRPC {}, REST {})", cfg.grpcAddress(), cfg.restAddress());
        return builder.build();
    }
}
