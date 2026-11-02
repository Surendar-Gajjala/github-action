package com.gsr.scm.workflow.engine.camunda;

import com.gsr.scm.common.Exceptions.EngineUnavailableException;
import com.gsr.scm.workflow.engine.WorkflowEngineAdapter;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DeploymentEvent;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.Topology;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.Incident;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Camunda 8 implementation of the engine boundary, using the Camunda Java client (gRPC + REST). */
@Component
@ConditionalOnProperty(name = "scm.workflow.engine", havingValue = "camunda")
public class CamundaWorkflowAdapter implements WorkflowEngineAdapter {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final CamundaClient client;

    public CamundaWorkflowAdapter(CamundaClient client) {
        this.client = client;
    }

    @Override
    public EngineInfo info() {
        try {
            Topology topology = client.newTopologyRequest().send().get(3, TimeUnit.SECONDS);
            return new EngineInfo("CAMUNDA_8", "Camunda 8", true, true,
                    "Gateway " + topology.getGatewayVersion() + ", " + topology.getBrokers().size() + " broker(s), "
                            + topology.getPartitionsCount() + " partition(s)");
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new EngineInfo("CAMUNDA_8", "Camunda 8", true, false,
                    "Cluster not reachable: " + e.getMessage());
        }
    }

    @Override
    public DeploymentResult deploy(String processId, String bpmnXml) {
        DeploymentEvent event = call("deploy " + processId, () -> client.newDeployResourceCommand()
                .addResourceStringUtf8(bpmnXml, processId + ".bpmn")
                .requestTimeout(REQUEST_TIMEOUT)
                .send().join());
        Process process = event.getProcesses().stream()
                .filter(p -> processId.equals(p.getBpmnProcessId()))
                .findFirst()
                .orElseThrow(() -> new EngineUnavailableException(
                        "Camunda accepted the deployment but returned no process '" + processId + "'", null));
        return new DeploymentResult(process.getProcessDefinitionKey(), process.getVersion());
    }

    @Override
    public long startInstance(long processDefinitionKey, Map<String, Object> variables) {
        return call("start process instance", () -> client.newCreateInstanceCommand()
                .processDefinitionKey(processDefinitionKey)
                .variables(variables)
                .requestTimeout(REQUEST_TIMEOUT)
                .send().join()
                .getProcessInstanceKey());
    }

    @Override
    public void completeUserTask(long jobKey, Map<String, Object> variables) {
        call("complete user task job " + jobKey, () -> client.newCompleteCommand(jobKey)
                .variables(variables)
                .requestTimeout(REQUEST_TIMEOUT)
                .send().join());
    }

    @Override
    public void retry(long engineInstanceKey, Long failedJobKey) {
        if (failedJobKey == null) {
            throw new EngineUnavailableException("No failed job recorded for instance " + engineInstanceKey
                    + "; resolve the incident in Camunda Operate", null);
        }
        call("update job retries", () -> client.newUpdateRetriesCommand(failedJobKey).retries(3)
                .requestTimeout(REQUEST_TIMEOUT).send().join());
        List<Incident> incidents = call("search incidents", () -> client.newIncidentSearchRequest()
                .filter(f -> f.jobKey(failedJobKey).state(IncidentState.ACTIVE))
                .send().join().items());
        for (Incident incident : incidents) {
            call("resolve incident " + incident.getIncidentKey(), () -> client
                    .newResolveIncidentCommand(incident.getIncidentKey()).requestTimeout(REQUEST_TIMEOUT).send().join());
        }
    }

    private static <T> T call(String action, Supplier<T> command) {
        try {
            return command.get();
        } catch (RuntimeException e) {
            throw new EngineUnavailableException("Camunda 8 could not " + action + ": " + e.getMessage(), e);
        }
    }
}
