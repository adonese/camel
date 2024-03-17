package com.iso;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Utils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void sendToFastAPIEndpoint(String aggregatedPayments) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost("http://localhost:8088/pacs008-analytics");

            // Convert the JSON array to a List of Maps
            List<Map<String, Object>> jsonArray = objectMapper.readValue(aggregatedPayments, new TypeReference<List<Map<String, Object>>>() {});

            // Set the List of Maps as the request body
            httpPost.setEntity(new StringEntity(objectMapper.writeValueAsString(jsonArray)));
            httpPost.setHeader("Content-Type", "application/json");

            HttpResponse response = httpClient.execute(httpPost);
            System.out.println("FastAPI response: " + response);
        } catch (IOException e) {
            // Handle the exception appropriately (e.g., log it or throw a custom exception)
            throw new RuntimeException("Error sending data to FastAPI endpoint", e);
        }
    }
}

