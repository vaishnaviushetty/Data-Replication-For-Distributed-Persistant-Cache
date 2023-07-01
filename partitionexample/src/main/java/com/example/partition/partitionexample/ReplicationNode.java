package com.example.partition.partitionexample;

import com.google.gson.*;
import io.etcd.jetcd.*;
import io.etcd.jetcd.kv.*;
import io.etcd.jetcd.watch.*;
import io.kubernetes.client.openapi.*;
import io.kubernetes.client.openapi.apis.*;
import io.kubernetes.client.openapi.models.*;
import mousio.etcd4j.*;
import org.apache.commons.lang3.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.stereotype.*;

import java.io.*;
import java.net.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;

@Component
public class ReplicationNode {
    private final EtcdClient etcdClient;
    private final boolean isLeader;
    private ExecutorService executorService;

    @Autowired
    private EmployeeController employeeController;

    public void setEmployeeController(EmployeeController employeeController) {
        this.employeeController = employeeController;
    }

    public EmployeeController getEmployeeController() {
        return employeeController;
    }

    public static final int NUM_PARTITIONS = 5;
    public static final String BASE_DIR = "/mnt/data/";
    private static final Map<String, Map<String, Employee>> PARTITIONS = new HashMap<>();

    private KV kvClient;

    static {
        // Initialize partition maps
        for (int i = 1; i <= NUM_PARTITIONS; i++) {
            PARTITIONS.put("P" + i, new HashMap<>());
        }
    }

    public ReplicationNode(String endpoint, boolean isLeader) {
        this.etcdClient = new EtcdClient(URI.create(endpoint));
        this.isLeader = isLeader;
    }


    public void start() throws Exception {
        if (isLeader) {
            leaderLoop();
        } else {
            followerLoop();
        }
    }

    private void leaderLoop() throws Exception {
        while (true) {
            if (employeeController.getNewEmployee().isEmpty()) {
                return;
            }
    
            List<Employee> newEmployees = employeeController.getNewEmployee();
            for (Employee emp : newEmployees) {
                CompletableFuture<GetResponse> getFuture = kvClient.get(ByteSequence.from("/data", Charset.defaultCharset()));
                GetResponse response = getFuture.get();
    
                if (response.getKvs().isEmpty()) {
                    // Key not found, handle accordingly
                    continue;
                }
    
                KeyValue kv = response.getKvs().get(0);
                String value = kv.getValue().toString(Charset.defaultCharset());
    
                // Process the value as required
                // Employee emp = parseEmployeeFromResponse(value);
                // Serialize the emp object to JSON or any other format
    
                byte[] empBytes = SerializationUtils.serialize((Serializable) emp);
    
                // Convert the byte array to a String if necessary
                String empSerialized = new String(empBytes);
                ByteSequence key = ByteSequence.from("/data", Charset.defaultCharset());
                ByteSequence val = ByteSequence.from(empSerialized, Charset.defaultCharset());
                CompletableFuture<PutResponse> putFuture = kvClient.put(key, val);
                putFuture.get();
            }
        }
    }
    
    private void followerLoop() {
        ByteSequence leaderKey = ByteSequence.from("/leader", Charset.defaultCharset());
        ByteSequence dataKey = ByteSequence.from("/data", Charset.defaultCharset());

        Watch watch = (Watch) etcdClient.getAll();
        watch.watch(leaderKey, watchResponse -> {
            for (WatchEvent event : watchResponse.getEvents()) {
                // Check for leader changes
                WatchEvent.EventType eventType = event.getEventType();
                if (eventType == WatchEvent.EventType.PUT) {
                    // Replicate changes from leader
                    CompletableFuture<GetResponse> getFuture = kvClient.get(dataKey);
                    getFuture.thenAccept(response -> {
                        for (KeyValue kv : response.getKvs()) {
                            byte[] empBytes = kv.getValue().getBytes();

                            // Deserialize the byte array to an Employee object
                            Employee emp = SerializationUtils.deserialize(empBytes);

                            employeeController.insertEmployee(emp);
                            // Update the follower cache and perform other operations
                           // updateFollowerCache(emp);
                            //updateFollowerPersistentVolume(emp);
                        }
                    });
                }
            }
        });
    }
   /* private Map<String, Employee> followerCache = new HashMap<>();
    private void updateFollowerCache(Employee emp) {
        // Update follower cache with the received employee data
        followerCache.put(emp.getId(), emp);
    }

    private void updateFollowerPersistentVolume( Employee emp) {
        // Update follower's persistent volume with the received employee data
        String partition = getPartitionForId(emp.getId());
        String filePath = BASE_DIR + partition + "/" + emp.getId() + ".json";
        try {
            JSONUtils.writeJSONToFile(emp, filePath);
            System.out.println("Updated employee " + emp.getId() + " in file: " + filePath);
        } catch (IOException e) {
            // Handle exception
        }

    }
*/
    private String getPartitionForId(String id) {
        int partitionIndex = Math.floorMod(id.hashCode(), NUM_PARTITIONS) + 1;
        return "P" + partitionIndex;
    }


    private Employee parseEmployeeFromResponse(String responseBody) {
        // Parse the response body and convert it to an Employee object
        // Implement the logic to parse the response and create the Employee object

        // Example:
        Employee employee = new Employee();
        // Parse the response body JSON and set the values accordingly
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        employee.setId(jsonObject.get("id").getAsString());
        employee.setName(jsonObject.get("name").getAsString());
        employee.setSalary(jsonObject.get("salary").getAsDouble());
        employee.setDeleted(jsonObject.get("isDeleted").getAsBoolean());

        return employee;
    }
    public void destroy() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public static void main(String[] args) throws Exception {
        ReplicateDataService replicateDataService = new ReplicateDataService();
        replicateDataService.init();
    }

}

class ReplicateDataService {
    private KV kvClient;

    private ExecutorService executorService;
    public void init() {
        try {
            // Read pod details from Kubernetes API
            ApiClient client = io.kubernetes.client.util.Config.defaultClient();
            Configuration.setDefaultApiClient(client);

            CoreV1Api api = new CoreV1Api(client);
            V1PodList podList = api.listPodForAllNamespaces((Boolean)true,"null" , "status.phase=Running", "app=partition-restapi",(Integer) 5,"true", "null","null", (Integer)30,(Boolean) true);
            List<V1Pod> pods = podList.getItems();
            String[] etcdEndpoints = new String[pods.size()];

            // Create executor service
            executorService = Executors.newFixedThreadPool(etcdEndpoints.length);
            Future<?>[] futures = new Future[etcdEndpoints.length];

            // Create client using endpoints
            Client etcdClient = Client.builder().endpoints(etcdEndpoints).build();
            this.kvClient = etcdClient.getKVClient();

            // Extract endpoints from pod details and Check pod labels to determine role
            for (int i = 0; i < pods.size(); i++) {
                V1Pod pod = pods.get(i);
                String endpoint = "http://" + pod.getStatus().getPodIP() + ":2379";
                etcdEndpoints[i] = endpoint;
                Map<String,String> podLabels= pod.getMetadata().getLabels();


                if (podLabels != null) {
                    String role = podLabels.get("role");
                    boolean isLeader = "leader".equals(role);
                    ReplicationNode node = new ReplicationNode(endpoint, isLeader);
                    futures[i] = executorService.submit(() -> {
                        try {
                            node.start();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            }

            // Wait for completion of all nodes
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (ApiException | InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
