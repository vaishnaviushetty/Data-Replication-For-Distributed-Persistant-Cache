package com.example.partition.partitionexample;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;


public class JSONUtils {
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void writeJSONToFile(Object obj, String filePath) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(new File(filePath));
            mapper.writeValue(writer, obj);
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    public static Employee readJSONFromFile(String path, Class<Employee> employeeClass) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        File file = new File(path);
        FileInputStream fileInputStream = new FileInputStream(file);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            stringBuilder.append(line);
        }
        String jsonString = stringBuilder.toString();
        return objectMapper.readValue(jsonString, employeeClass);
    }


    public static void deleteFileContents(String filepath) throws IOException {
        File file = new File(filepath);
        FileWriter fileWriter = new FileWriter(file);
        fileWriter.write("");
        fileWriter.flush();
        fileWriter.close();
    }
}
