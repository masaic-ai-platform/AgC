import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.temporal.authorization.AuthorizationGrpcMetadataProvider;
import io.temporal.authorization.AuthorizationTokenSupplier;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.AgcRuntimeTool;
import tools.ToolRegistration;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class ApplicationStart {
    private static final Logger logger = LoggerFactory.getLogger(ApplicationStart.class);
    private static final String B64 = "U2FsdGVkX1/vaKfU2w6MUao0RBej4XM7it74NY2klDRmDTiO9qE04wVqBqDcsSujIvBMk6yDOHcQ9mb1PA046S5LrpQeLa1M01us/CnbtltbOEwSIz+Eq4HyP5XXrb1bsXXzrxak+llPh72hAZ3LRaOfEULb97RoVAmRe2EhYoSGFv65CElPU741ez7BMzG+0mcF6hvBETQazKNeHmfuvUgNvr4cGmgGAJLQ1v2dlJMVi01nUXoBAI+JQLdltS0j43tmwmG8Diu0gwT6H+mtW+rKx4IsBrdfsk5/+yhtFsfd+IVdRortmjKXir1HLg31PGM/ZzbIbox7hDUFJGvBEWTmf3cd5VqhMzd60q+6zKQNfr91lIxblS5VozeGzX4xDRrA38l2KiWuVS9AfR3V7y+LSypoTI1rHVHFBot+TK1q02XjR+ZMFwLFy/o0NiAVRpsFOvYE/Zob+koqTxwgc3HDV9odFQNtHOrVAC6g5kspvwj+6fpZll6/T8C/R8qfh/qDFm79QPILgc5/bc+fcuG9OEgEVKhKXgRNUSlJv3KHKG32x5AxhXIifXAEjWGkTDixI+N6wvfyQ1USFuXAltwOnwUxKYb7loVG99oonXeybhhtzx0xhFT2KXnS1Y4W4tvZ/FmwwsIl0E4hNe/ucTDuKj9gBDsiayPcwUhG5665ckbFaJGta/U9n9b+GSfj";
    private static final String OPENSSL_HDR = "Salted__";
    private static final int SALT_LEN = 8, KEY_LEN = 32, IV_LEN = 16, ITER = 100000;

    public static void main(String[] args) throws Exception {
        logger.info("###Starting ClientRuntime Java SDK ####");
        Map<String, String> cred = decrypt(B64);
        String target = cred.get("target");
        String namespace = cred.get("namespace");
        String apiKey = cred.get("apiKey");
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

        // Warm up tool registry and ensure at least one tool is available
        java.util.Collection<AgcRuntimeTool> discoveredTools = ToolRegistration.getAll();
        if (discoveredTools == null || discoveredTools.isEmpty()) {
            logger.warn("No client-side tools discovered; worker factory will not be started.");
            return;
        }

        // Build worker factory and start a worker per toolId
        WorkerFactory factory = WorkerFactory.newInstance(client);
        for (AgcRuntimeTool tool : ToolRegistration.getAll()) {
            String taskQueue = tool.toolId();
            Worker worker = factory.newWorker(taskQueue);
            worker.registerActivitiesImplementations(tool);
            logger.info("Registered tool '{}' on task queue '{}'", tool.getClass().getSimpleName(), taskQueue);
        }
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

    private static Map<String, String> decrypt(String base64) throws Exception {
        byte[] all = Base64.getDecoder().decode(base64);
        if (all.length < 16 || !OPENSSL_HDR.equals(new String(all, 0, 8, StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Not OpenSSL salted format");
        byte[] salt = new byte[SALT_LEN];
        System.arraycopy(all, 8, salt, 0, SALT_LEN);
        byte[] ct = new byte[all.length - 16];
        System.arraycopy(all, 16, ct, 0, ct.length);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(("").toCharArray(), salt, ITER, (KEY_LEN + IV_LEN) * 8);
        byte[] keyIv = skf.generateSecret(spec).getEncoded();
        byte[] key = new byte[KEY_LEN], iv = new byte[IV_LEN];
        System.arraycopy(keyIv, 0, key, 0, KEY_LEN);
        System.arraycopy(keyIv, KEY_LEN, iv, 0, IV_LEN);
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        byte[] plain = c.doFinal(ct);
        return new ObjectMapper().readValue(plain, new TypeReference<Map<String, String>>() {
        });
    }
}

