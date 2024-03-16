package com.iso;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.dataformat.JsonLibrary;

public class Pacs008Integration {
    public static void main(String[] args) throws Exception {
        CamelContext context = new DefaultCamelContext();

        // Configure Jackson data format for JSON conversion
        JacksonDataFormat jacksonDataFormat = new JacksonDataFormat();
        jacksonDataFormat.setUnmarshalType(java.util.Map.class);

        // Sender route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:src/main/resources/pacs008?noop=true")
                        .log("Sending PACS.008 message: ${body}")
                        .to("activemq:queue:pacs008");
            }
        });

        // Receiver route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:pacs008")
                        .log("Received PACS.008 message: ${body}")
                        .unmarshal().jacksonXml()
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // Extract relevant information from the PACS.008 message
                                java.util.Map<String, Object> pacs008 = exchange.getIn().getBody(java.util.Map.class);
                                String debtorName = (String) pacs008.get("Dbtr.Nm");
                                String creditorName = (String) pacs008.get("Cdtr.Nm");
                                Double amount = (Double) pacs008.get("Amt.InstdAmt._");
                                String currency = (String) pacs008.get("Amt.InstdAmt.Ccy");

                                // Perform business logic or further processing
                                // For example, update account balances, generate notifications, etc.
                                log.info("Processing payment: Debtor={}, Creditor={}, Amount={}, Currency={}",
                                        debtorName, creditorName, amount, currency);

                                // Set the processed information back to the exchange
                                exchange.getIn().setBody(pacs008);
                            }
                        })
                        .marshal().json(JsonLibrary.Jackson)
                        .log("Processed PACS.008 message: ${body}")
                        .to("file:target/processed_pacs008");
            }
        });

        context.start();
        Thread.sleep(10000);
        context.stop();
    }
}