package reference;

import io.temporal.authorization.AuthorizationGrpcMetadataProvider;
import io.temporal.authorization.AuthorizationTokenSupplier;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class ClientSideToolRunner {
    private static List<ClientSideTool> tools = new ArrayList<ClientSideTool>();

    public static void registerTool(ClientSideTool tool) {
        tools.add(tool);
    }

    public static void main(String[] args) {
        // ---- Temporal Cloud connection (API-key + TLS) ----
        String target = System.getenv("PLATFORM_DEPLOYMENT_TEMPORAL_TARGET");
        String namespace = System.getenv("PLATFORM_DEPLOYMENT_TEMPORAL_NAMESPACE");
        String apiKey = System.getenv("PLATFORM_DEPLOYMENT_TEMPORAL_API-KEY");

        AuthorizationTokenSupplier tokenSupplier = () -> "Bearer " + apiKey;
        WorkflowServiceStubsOptions serviceOptions = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target)
                .setEnableHttps(true)
                .addGrpcMetadataProvider(new AuthorizationGrpcMetadataProvider(tokenSupplier))
                .build();

        WorkflowServiceStubs service = WorkflowServiceStubs.newServiceStubs(serviceOptions);

        WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build();

        WorkflowClient client = WorkflowClient.newInstance(service, clientOptions);

        // ---- Worker hosting the ACTIVITY ONLY ----
        //start factory for each tool available in tools list....
//        WorkerFactory factory = WorkerFactory.newInstance(client);
//        Worker actWorker = factory.newWorker("jb.add_two_numbers");
//        actWorker.registerActivitiesImplementations(new ClientSideToolExecutionActivityImpl());
//        factory.start();

        System.out.println("App B: Activity worker started on jb.add_two_numbers  (namespace=" + namespace + ", target=" + target + ")");

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("App B: Shutting down activity worker...");
            factory.shutdown();
            try {
                factory.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        //stop factory for each tool available in tools list....

        // Keep the activity worker alive
        Thread.currentThread().join();

    }
}
