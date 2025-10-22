import common.AgCClientSideTool;
import common.Util;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.temporal.authorization.AuthorizationGrpcMetadataProvider;
import io.temporal.authorization.AuthorizationTokenSupplier;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import tools.ApplyRetentionActions;
import tools.SelectRetentionCohort;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ApplicationStart {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationStart.class);
    private static final String B64 = System.getenv("AGC_CREDS");

    public static void main(String[] args) throws Exception {
        logger.info("###Starting ClientRuntime Java SDK ####");
        Map<String, String> cred = Util.decrypt(B64);
        String target = cred.get("target");
        String namespace = cred.get("namespace");
        String apiKey = cred.get("apiKey");
        String userId = cred.get("userId");
        AuthorizationTokenSupplier tokenSupplier = () -> "Bearer " + apiKey;
        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                .setEnableHttps(true)
                .addGrpcMetadataProvider(new AuthorizationGrpcMetadataProvider(tokenSupplier))
                .build();
        //Create worker Service
        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);
        WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build();
        WorkflowClient client = WorkflowClient.newInstance(service, clientOptions);
        WorkerFactory factory = WorkerFactory.newInstance(client);
        //Get instance of tools
        AgCClientSideTool retentionCohortTool = new SelectRetentionCohort();
        AgCClientSideTool applyRetentionTool = new ApplyRetentionActions();
        //Add tools to factory of worker
        factory.newWorker(userId+"."+retentionCohortTool.toolId()).registerActivitiesImplementations(retentionCohortTool);
        factory.newWorker(userId+"."+applyRetentionTool.toolId()).registerActivitiesImplementations(applyRetentionTool);
        factory.start();
        //Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down worker factory and service...");
            try {
                factory.shutdown();
                factory.awaitTermination(10, TimeUnit.SECONDS);
            } catch (Throwable ignored) {
            }
            try {
                service.shutdown();
            } catch (Throwable ignored) {
            }
        }));
        //keep the worker alive
        Thread.currentThread().join();
    }
}
