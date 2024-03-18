package com.iso;

import org.apache.camel.Exchange;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Pacs008Processor implements org.apache.camel.Processor {
    @Override
    public void process(Exchange exchange) throws Exception {
        // Extract relevant information from the PACS.008 message
        Map<String, Object> pacs008 = exchange.getIn().getBody(Map.class);
        if (pacs008 == null) {
            // Set a default value for the body if it's null
            pacs008 = new HashMap<>();
        }
        String messageId = null;
        String debtorName = null;
        String creditorName = null;
        Double amount = null;
        String currency = null;

        if (pacs008 != null) {
            Map<String, Object> fiToFICstmrCdtTrf = (Map<String, Object>) pacs008.get("FIToFICstmrCdtTrf");
            if (fiToFICstmrCdtTrf != null) {
                Map<String, Object> grpHdr = (Map<String, Object>) fiToFICstmrCdtTrf.get("GrpHdr");
                if (grpHdr != null) {
                    messageId = (String) grpHdr.get("MsgId");
                }

                List<Map<String, Object>> cdtTrfTxInfList = (List<Map<String, Object>>) fiToFICstmrCdtTrf.get("CdtTrfTxInf");
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
        }

        // Perform business logic or further processing
        // For example, update account balances, generate notifications, etc.
        System.out.println(String.format("Processing payment: MessageId=%s, Debtor=%s, Creditor=%s, Amount=%s, Currency=%s",
                messageId, debtorName, creditorName, amount, currency));

        // Create a new Map to store the processed pacs008 data
        Map<String, Object> processedPacs008 = new HashMap<>();
        processedPacs008.put("messageId", messageId);
        processedPacs008.put("debtorName", debtorName);
        processedPacs008.put("creditorName", creditorName);
        processedPacs008.put("amount", amount);
        processedPacs008.put("currency", currency);

        // Set the messageId in the exchange headers
        exchange.getIn().setHeader("MessageId", messageId);

        // Set the processed pacs008 data in the exchange body
        exchange.getIn().setBody(processedPacs008, Map.class);

    }


}
