package com.example.partition.partitionexample;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
@RestController
public class EmployeeController {

    public static final int NUM_PARTITIONS = 5;
    public static final String BASE_DIR = "/mnt/data/";
    private static final Map<String, Map<String, Employee>> PARTITIONS = new HashMap<>();
    public static final String id = UUID.randomUUID().toString();
    private List newEmployee=new ArrayList();

    public void setNewEmployee(List newEmployee) {
        this.newEmployee = newEmployee;
    }

    public List getNewEmployee() {
        return newEmployee;
    }

    static {
        // Initialize partition maps
        for (int i = 1; i <= NUM_PARTITIONS; i++) {
            PARTITIONS.put("P" + i, new HashMap<>());
        }
    }

    //Get the employee from local cache with the employee key id value
    @GetMapping("/employee/{id}")
    public ResponseEntity<Employee> getEmployee(@PathVariable String id) {
        String partition = getPartitionForId(id);
        Map<String, Employee> partitionMap = getPartitionMap(partition);
        Employee employee = partitionMap.get(id);
        if (employee == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } else {
            return new ResponseEntity<>(employee, HttpStatus.OK);
        }
    }

    //Get the employee from the partition file with the employee key id value
    @GetMapping("/partition/{partition}/employee/{id}")
    public ResponseEntity<Employee> getEmployeeInPartition(@PathVariable String partition, @PathVariable String id) {
        String filePath = BASE_DIR + partition + "/" + id + ".json";
        try {
            Employee employee = JSONUtils.readJSONFromFile(filePath, Employee.class);
            return new ResponseEntity<>(employee, HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    //Create the employee and add to any one of the partition file
    @PostMapping("/employee")
    public ResponseEntity<Void> createEmployee(@RequestBody Employee employee) {
        String id = UUID.randomUUID().toString();
        System.out.println(id);
        employee.setId(id);
        insertEmployee(employee);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    //Get the entire partition file details with the partition key value
    @GetMapping("/partition/{partition}")
    public ResponseEntity<Map<String, Map<String, Employee>>> getEmployeesInPartition(@PathVariable String partition) {
        String partitionDir = BASE_DIR + partition + "/";
        File dir = new File(partitionDir);
        if (!dir.exists()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

        Map<String, Map<String, Employee>> partitionMap = new HashMap<>();
        Map<String, Employee> employeeMap = new HashMap<>();
        for (File file : dir.listFiles()) {
            try {
                Employee employee = JSONUtils.readJSONFromFile(file.getPath(), Employee.class);
                employeeMap.put(employee.getId(), employee);
            } catch (IOException e) {
                // Log the error
            }
        }
        partitionMap.put(partition, employeeMap);

        return new ResponseEntity<>(partitionMap, HttpStatus.OK);
    }

    //To delete the elements from the file
    @DeleteMapping("/{partition}/{id}")
    public ResponseEntity<String> deleteEmployee(@PathVariable String partition, @PathVariable String id) {
        String filePath = BASE_DIR + partition + "/" + id + ".json";
        try {
            Employee employee = JSONUtils.readJSONFromFile(filePath, Employee.class);
            if (employee == null) {
                return new ResponseEntity<>("Employee not found", HttpStatus.NOT_FOUND);
            }
            if (employee.isDeleted()) {
                return new ResponseEntity<>("Employee already deleted", HttpStatus.BAD_REQUEST);
            }
            employee.setDeleted(true);
            System.out.println("Deleted employee " + id);
            writePartitionToDisk(partition);
            JSONUtils.deleteFileContents(filePath);
            return new ResponseEntity<>("Employee deleted", HttpStatus.OK);
        } catch (IOException e) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }

    }


    private void writePartitionToDisk(String partition) {
        Map<String, Employee> partitionMap = PARTITIONS.get(partition);
        String partitionDir = BASE_DIR + partition + "/";
        File dir = new File(partitionDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        for (String id : partitionMap.keySet()) {
            String filePath = partitionDir + id + ".json";
            try {
                Employee employee = partitionMap.get(id);
                System.out.println(employee);
                if (employee.isDeleted()) {
                    new File(filePath).delete();
                    System.out.println("Deleted employee " + id + " from file: " + filePath);
                } else {
                    JSONUtils.writeJSONToFile(partitionMap.get(id), filePath);
                    System.out.println("Wrote employee " + id + " to file: " + filePath);
                }
            } catch (IOException e) {
                // Handle exception
            }
        }
    }
    public void insertEmployee(Employee employee){
        newEmployee.add(employee);
        String partition = getPartitionForId(id);
        Map<String, Employee> partitionMap = getPartitionMap(partition);
        partitionMap.put(id, employee);
        writePartitionToDisk(partition);
    }

    private String getPartitionForId(String id) {
        int partitionIndex = Math.floorMod(id.hashCode(), NUM_PARTITIONS) + 1;
        return "P" + partitionIndex;
    }


    private Map<String, Employee> getPartitionMap(String partition) {
        if (!PARTITIONS.containsKey(partition)) {
            PARTITIONS.put(partition, new HashMap<>());
        }
        return PARTITIONS.get(partition);
    }

}
