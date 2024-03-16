package com.iso;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.dataformat.JsonLibrary;

import java.io.FileNotFoundException;
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

                                    Map<String, Object> cdtTrfTxInf = (Map<String, Object>) pacs008.get("CdtTrfTxInf");
                                    if (cdtTrfTxInf != null) {
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
                                log.info("Processing payment: MessageId={}, Debtor={}, Creditor={}, Amount={}, Currency={}",
                                        messageId, debtorName, creditorName, amount, currency);

                                // Set the processed information back to the exchange
                                exchange.getIn().setHeader("MessageId", messageId);
                                exchange.getIn().setBody(pacs008);
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
                        .marshal().json(JsonLibrary.Jackson)
                        // Write the audited payment to a database or file
                        .to("file:target/audited-payments?fileName=${header.MessageId}.json")
                        .to("log:payment-audit");
            }
        });

        // Aggregator route
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:target/audited-payments?noop=true&include=.*\\.json")
                        .routeId("payment-aggregator")
                        .onException(FileNotFoundException.class)
                        .handled(true)
                        .log("File not found: ${header.CamelFileAbsolutePath}")
                        .to("log:file-not-found")
                        .end()
                        .log("Aggregating payment: ${body}")
                        .aggregate(constant(true), new AggregationStrategy() {
                            @Override
                            public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
                                if (oldExchange == null) {
                                    return newExchange;
                                }
                                String oldBody = oldExchange.getIn().getBody(String.class);
                                String newBody = newExchange.getIn().getBody(String.class);
                                String aggregatedBody = oldBody + ",\n" + newBody;
                                oldExchange.getIn().setBody("[" + aggregatedBody + "]");
                                return oldExchange;
                            }
                        })
                        .completionSize(10)
                        .completionTimeout(5000)
                        .log("Aggregated payments: ${body}")
                        .to("file:target/aggregated-payments?fileName=aggregated-payments.json");
            }
        });

        context.start();
        Thread.sleep(60000); // Run for 1 minute
        context.stop();
    }
}