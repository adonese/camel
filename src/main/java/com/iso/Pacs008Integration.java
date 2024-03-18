package com.iso;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.jacksonxml.JacksonXMLDataFormat;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.apache.camel.http.common.HttpMethods;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.dataformat.JsonLibrary;

import java.io.FileNotFoundException;
import java.util.*;

import java.util.Map;

public class Pacs008Integration {
    public static void main(String[] args) throws Exception {
        CamelContext context = new DefaultCamelContext();

        // Configure Jackson data format for JSON conversion
        JacksonDataFormat jacksonDataFormat = new JacksonDataFormat();
        jacksonDataFormat.setUnmarshalType(Map.class);

        // Sender route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:src/main/resources/pacs008?noop=true")
                        .routeId("pacs008-sender")
                        .log("Sending PACS.008 message: ${body}")
                        .to("activemq:queue:pacs008");
            }
        });

        // Receiver route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:pacs008")
                        .routeId("pacs008-receiver")
                        .to("direct:processPacs008");

                from("direct:processPacs008")
                        .routeId("pacs008-processor")
                        .log("Received PACS.008 message: ${body}")
                        .unmarshal().jacksonXml()
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // Extract relevant information from the PACS.008 message
                                Map<String, Object> pacs008 = exchange.getIn().getBody(Map.class);
                                String messageId = null;
                                String debtorName = null;
                                String creditorName = null;
                                Double amount = null;
                                String currency = null;

                                if (pacs008 != null) {
                                    Map<String, Object> grpHdr = (Map<String, Object>) pacs008.get("GrpHdr");
                                    if (grpHdr != null) {
                                        messageId = (String) grpHdr.get("MsgId");
                                    }

                                    List<Map<String, Object>> cdtTrfTxInfList = (List<Map<String, Object>>) pacs008.get("CdtTrfTxInf");
                                    if (cdtTrfTxInfList != null && !cdtTrfTxInfList.isEmpty()) {
                                        Map<String, Object> cdtTrfTxInf = cdtTrfTxInfList.get(0);
                                        Map<String, Object> dbtr = (Map<String, Object>) cdtTrfTxInf.get("Dbtr");
                                        if (dbtr != null) {
                                            debtorName = (String) dbtr.get("Nm");
                                        }

                                        Map<String, Object> cdtr = (Map<String, Object>) cdtTrfTxInf.get("Cdtr");
                                        if (cdtr != null) {
                                            creditorName = (String) cdtr.get("Nm");
                                        }

                                        Map<String, Object> amt = (Map<String, Object>) cdtTrfTxInf.get("Amt");
                                        if (amt != null) {
                                            Map<String, Object> instdAmt = (Map<String, Object>) amt.get("InstdAmt");
                                            if (instdAmt != null) {
                                                amount = Double.parseDouble((String) instdAmt.get(""));
                                                currency = (String) instdAmt.get("Ccy");
                                            }
                                        }
                                    }
                                }

                                // Perform business logic or further processing
                                // For example, update account balances, generate notifications, etc.
                                System.out.println(String.format("Processing payment: MessageId=%s, Debtor=%s, Creditor=%s, Amount=%s, Currency=%s",
                                        messageId, debtorName, creditorName, amount, currency));

                                // Set the processed information back to the exchange
                                exchange.getIn().setHeader("MessageId", messageId);
                                exchange.getIn().setBody(pacs008);
                            }
                        }).process(exchange -> {
                            // Generate random payment status
                            Random random = new Random();
                            String[] statuses = {"ACCP", "RJCT", "UNKN"};
                            String paymentStatus = statuses[random.nextInt(statuses.length)];

                            // Send payment status to PACS.002 service, to a golang service
                            String paymentStatusUrl = "http://0.0.0.0:8082/pacs002?bridgeEndpoint=true";
                            String messageId = exchange.getIn().getHeader("MessageId", String.class);
                            String paymentStatusJson = String.format(
                                    "{\"messageId\":\"%s\",\"originalMessage\":{\"messageId\":\"%s\"},\"status\":\"%s\"}",
                                    UUID.randomUUID(), messageId, paymentStatus);

                            exchange.getIn().setBody(paymentStatusJson);
                            exchange.getIn().setHeader(Exchange.HTTP_METHOD, HttpMethods.POST);
                            exchange.getIn().setHeader("Content-Type", "application/json");

                            try {
                                Exchange response = exchange.getContext().createProducerTemplate().send(paymentStatusUrl, exchange);
                                if (response.isFailed()) {
                                    throw response.getException();
                                }
                                // Handle the response from the Golang endpoint if needed
                                String responseBody = response.getIn().getBody(String.class);
                                System.out.println("Response from Golang endpoint: " + responseBody);

                                // Set the response body back to the exchange for further processing
                                exchange.getIn().setBody(responseBody);
                            } catch (Exception e) {
                                // Handle the exception appropriately
                                System.err.println("Error sending payment status to Golang endpoint: " + e.getMessage());
                                // You can rethrow the exception or handle it based on your requirements
                                throw new RuntimeException("Error sending payment status to Golang endpoint", e);
                            }
                        })
                        .multicast()
                        .to("direct:notify", "direct:audit");

                from("direct:notify")
                        .routeId("payment-notification")
                        .log("Sending payment notification for MessageId: ${header.MessageId}")
                        // Perform notification logic, e.g., send email or SMS
                        .to("log:payment-notification");

                from("direct:audit")
                        .routeId("payment-audit")
                        .log("Auditing payment for MessageId: ${header.MessageId}")
                        .process(exchange -> {
                            // Convert the body to a Map
                            Map<String, Object> pacs008 = exchange.getIn().getBody(Map.class);
                            exchange.getIn().setBody(pacs008);
                        })
                        .marshal().json(JsonLibrary.Jackson)
                        // Write the audited payment to a file
                        .to("file:target/audited-payments?fileName=${header.MessageId}.json")
                        // Send the audited payment to the aggregator
                        .to("direct:aggregate")
                        .to("log:payment-audit");
            }
        });

        // HTTP endpoint route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://0.0.0.0:8080/pacs008")
                        .routeId("pacs008-http-endpoint")
                        .to("direct:processPacs008");
            }
        });

        // Aggregator route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:aggregate")
                        .routeId("payment-aggregator")
                        .log("Aggregating payment: ${body}")
                        .aggregate(constant(true), new AggregationStrategy() {
                            private ObjectMapper objectMapper = new ObjectMapper();

                            @Override
                            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                                try {
                                    String newBody = newExchange.getIn().getBody(String.class);
                                    Map<String, Object> newPayment = objectMapper.readValue(newBody, Map.class);

                                    if (oldExchange == null) {
                                        List<Map<String, Object>> aggregatedList = new ArrayList<>();
                                        aggregatedList.add(newPayment);
                                        String aggregatedBody = objectMapper.writeValueAsString(aggregatedList);
                                        newExchange.getIn().setBody(aggregatedBody);
                                        return newExchange;
                                    } else {
                                        String oldBody = oldExchange.getIn().getBody(String.class);
                                        List<Map<String, Object>> aggregatedList = objectMapper.readValue(oldBody, List.class);
                                        aggregatedList.add(newPayment);
                                        String aggregatedBody = objectMapper.writeValueAsString(aggregatedList);
                                        oldExchange.getIn().setBody(aggregatedBody);
                                        return oldExchange;
                                    }
                                } catch (Exception e) {
                                    // Handle the exception appropriately
                                    throw new RuntimeException("Error aggregating payments", e);
                                }
                            }
                        })
                        .completionInterval(3000) // Aggregate every 3 seconds
                        .completionSize(10) // Aggregate up to 10 payments
                        .completionTimeout(5000) // Timeout if no new payments are received within 5 seconds
                        .log("Aggregated payments: ${body}")
                        .to("file:target/aggregated-payments?fileName=aggregated-payments.json");
            }
        });

        // Aggregated payments report route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://0.0.0.0:8081/aggregated-payments")
                        .routeId("aggregated-payments-report")
                        .log("Generating aggregated payments report")
                        .process(exchange -> {
                            // Read the aggregated payments file
                            String aggregatedPaymentsFile = "target/aggregated-payments/aggregated-payments.json";
                            String aggregatedPayments = "[]"; // Default empty JSON array

                            // Check if the file exists
                            File file = new File(aggregatedPaymentsFile);
                            if (file.exists()) {
                                // File exists, read its content
                                aggregatedPayments = exchange.getContext().getTypeConverter()
                                        .convertTo(String.class, file);
                            }

                            // Set the aggregated payments as the response body
                            exchange.getIn().setBody(aggregatedPayments);
                        })
                        .setHeader("Content-Type", constant("application/json"));
            }
        });

        // FastAPI endpoint route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/aggregated-payments?noop=true&include=.*\\.json")
                        .routeId("fastapi-pacs008-analytics")
                        .log("Sending aggregated payments to FastAPI endpoint")
                        .process(exchange -> {
                            // Read the aggregated payments file
                            String aggregatedPaymentsFile = exchange.getIn().getHeader("CamelFileAbsolutePath", String.class);
                            String aggregatedPayments = exchange.getContext().getTypeConverter()
                                    .convertTo(String.class, new File(aggregatedPaymentsFile));

                            // Check if there are any changes in the aggregated payments
                            String previousAggregatedPayments = exchange.getProperty("previousAggregatedPayments", String.class);
                            if (previousAggregatedPayments != null && previousAggregatedPayments.equals(aggregatedPayments)) {
                                System.out.println("No changes in aggregated payments. Skipping sending to FastAPI.");
                                return;
                            }

                            // Send the aggregated payments to the FastAPI endpoint
                            Utils.sendToFastAPIEndpoint(aggregatedPayments);

                            // Store the current aggregated payments for the next comparison
                            exchange.setProperty("previousAggregatedPayments", aggregatedPayments);
                        });
            }
        });

        // Start Camel context
        context.start();

        // Keep the application running until manually stopped
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                context.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        while (true) {
            Thread.sleep(1000);
        }
    }
}