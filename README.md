# pacs.008 Payment Processing System

## Introduction

I am proud to present the pacs.008 Payment Processing System, a comprehensive solution for handling and processing pacs.008 payment messages. This system demonstrates how one can design and implement scalable, distributed, and highly efficient payment processing architectures.

The idea is that I have been mostly doing iso8583 and other in-house apis, but i never really had the chance to get my hands dirty when it comes to iso20022 -- this a good starting point for me. Also, <https://adonese.sd/book> touched on many payment topics but still didn't manage to have any input on iso20022 this is the time.

_I didn't have any prior experience with apache camel before, this is the very first time I have had the chance to ever work with it_ if the code doesn't look like a camel best practice code, then it is because of that.

I'm also running this experiment in a totally new environment to me, that is apache camel and the whole enterprise integration business. I have tried through this project to cover these topics:
- file based integration, you can see how we are using apache camel to set up endpoints and routes for /aggregated-payments and /audited-payments -- file based integration while resembles a lot of experiences I have had before:
    - in pos, we had that concept of batch upload for merchants transactions settlement -- but even that api was deprecated and I didn't get to explore it in more details
- the extensibility of apache camel:
    - we are accepting a pacs.008 message (xml)
    - saving this message as a json for our other endpoints (aggregated and audited)
- in the `dev` branch, I also took the liberity to add `pacs002` for payment notification where I introduced an external integration with a golang endpoint <https://github.com/adonese/pacs002> -- this monorepo include both a golang program and a python one as well. The golang is used to aggregated pacs002 messages (availabe in dev branch, not main). The python one is used for setting up the aggregated data for post-processing using pandas, a library for processing structured data.

## System Components
The pacs.008 Payment Processing System consists of several key components that work together to provide a comprehensive payment processing solution:

### 1. Apache Camel Integration Framework
At the core of our system lies the Apache Camel integration framework. Camel is a powerful open-source integration framework that enables the routing, transformation, and processing of messages between various systems and protocols. We have leveraged Camel's extensive capabilities to design and implement the payment processing workflows.

Camel provides a wide range of components and enterprise integration patterns (EIPs) that allow us to define complex integration scenarios with ease. We have utilized Camel's DSL (Domain Specific Language) to define the routes and processors for handling pacs.008 payment messages.

We are also using activemq to give us a reliable messaging for our system.

### 2. pacs.008 Message Processing
The pacs.008 message format is a standardized ISO 20022 message type used for customer credit transfers. Our system is specifically designed to handle and process pacs.008 messages efficiently.

We have implemented a dedicated Camel route (`pacs008-processor`) that receives pacs.008 messages, unmarshals them from the XML format using the Jackson library, and extracts relevant information such as the message ID, debtor and creditor details, and payment amounts. This route performs the necessary business logic and further processing based on the extracted information.

### 3. Payment Status Notifications (available in dev branch)
_This is an early available feature we are introducing to add more features to our dummy financial system. For now, it is only available in `dev` branch but we are expecting to avail it in `main` in the next development cycle._

In addition to processing pacs.008 messages, our system also generates payment status notifications. After processing a pacs.008 message, the system sends a payment status notification to a PACS.002 service, which is implemented as a separate Go microservice.

The payment status notification includes details such as the original message ID and the status of the payment (e.g., accepted or rejected). This allows for real-time tracking and monitoring of payment statuses.

### 4. Payment Auditing and Aggregation
To ensure traceability and facilitate reconciliation, our system incorporates payment auditing and aggregation functionalities.

The `payment-audit` route is responsible for auditing processed payments. It converts the payment details to a JSON format using the Jackson library and writes them to a file for persistent storage. This allows for easy retrieval and analysis of payment audit trails.

Additionally, the `payment-aggregator` route aggregates processed payments based on predefined criteria, such as completion size and timeout. The aggregated payments are stored in a JSON file, providing a consolidated view of the payment transactions.

### 5. Reporting and Analytics (available in dev branch)
_This is an early available feature we are introducing to add more features to our dummy financial system. For now, it is only available in `dev` branch but we are expecting to avail it in `main` in the next development cycle._

Our system includes reporting and analytics capabilities to provide valuable insights into the payment processing operations.

The `aggregated-payments-report` route exposes an HTTP endpoint that generates an aggregated payments report. It reads the aggregated payments file and returns the data as a JSON response, allowing for easy integration with reporting and analytics tools.

Furthermore, we have integrated a FastAPI endpoint (`fastapi-pacs008-analytics`) that performs advanced analytics on the aggregated payment data. It calculates metrics such as total payment amount, currency counts, and maximum/minimum payment amounts. The analytics results are exposed via an HTML response, providing a user-friendly interface for viewing the analytics dashboard.

### 6. Deployment and Scalability (available in dev branch)
All of these 4 services are dockerized and can be deployed using docker-compose. We have also included a `docker-compose.yml` file that can be used to deploy the entire system with a single command. The idea is to have a single entry for this larger complicated system where you can just do `docker-compose up` and have the entire system up and running, and tear it down with `docker-compose down`.

## Real-World Use Case
Let's consider a real-world use case to illustrate the power and flexibility of the PACS.008 Payment Processing System. And this is frankly how I'm looking at this experiment:


- Imagine a large financial institution that processes thousands of customer credit transfers daily. The institution receives pacs.008 payment messages from various channels, such as file uploads, api requests, and message queues.

- The pacs.008 Payment Processing System seamlessly integrates with the institution's existing systems and processes these payment messages efficiently. The system extracts relevant information from the pacs.008 messages, performs necessary validations and transformations, and routes the payments to the appropriate downstream systems for further processing.

- As the payments are processed, the system generates real-time payment status notifications and sends them to the PACS.002 service. This allows the institution to provide timely updates to customers and stakeholders regarding the status of their payments.

- The payment auditing and aggregation functionalities of the system enable the institution to maintain a comprehensive audit trail of all processed payments. The aggregated payment data can be used for reconciliation, reporting, and analysis purposes.

## Golang and python apis

For the sake of convenience, one can access the projects herein: <https://github.com/adonese/pacs002> for both golang and python fastapi endpoints. But i'm also showing them here since they are rather small:

```go
package main

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strings"

	_ "github.com/mattn/go-sqlite3"
)

type PaymentStatus struct {
	MessageID       string `json:"messageId"`
	OriginalMessage struct {
		MessageID string `json:"messageId"`
	} `json:"originalMessage"`
	Status string `json:"status"`
}

var db *sql.DB

func main() {
	var err error
	db, err = sql.Open("sqlite3", "pacs002.db")
	if err != nil {
		log.Fatal(err)
	}
	defer db.Close()

	_, err = db.Exec(`CREATE TABLE IF NOT EXISTS payment_status (
        message_id TEXT PRIMARY KEY,
        original_message_id TEXT,
        status TEXT
    )`)
	if err != nil {
		log.Fatal(err)
	}

	http.HandleFunc("/pacs002", handlePacs002)
	http.HandleFunc("/pacs002-messages", getPacs002Messages)
	http.Handle("/", http.FileServer(http.Dir("static")))

	log.Println("PACS.002 status service listening on port 8082...")
	log.Fatal(http.ListenAndServe(":8082", nil))
}

func handlePacs002(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	var paymentStatus PaymentStatus
	err := json.NewDecoder(r.Body).Decode(&paymentStatus)
	if err != nil {
		http.Error(w, "Invalid request payload", http.StatusBadRequest)
		return
	}
	defer r.Body.Close()

	fmt.Printf("the request data is: %+v", paymentStatus)

	// Save payment status to SQLite database
	_, err = db.Exec(`INSERT INTO payment_status (message_id, original_message_id, status)
        VALUES (?, ?, ?)`, paymentStatus.MessageID, paymentStatus.OriginalMessage.MessageID, paymentStatus.Status)
	if err != nil {
		http.Error(w, "Failed to save payment status", http.StatusInternalServerError)
		return
	}

	w.WriteHeader(http.StatusAccepted)
}

func getPacs002Messages(w http.ResponseWriter, r *http.Request) {
	rows, err := db.Query("SELECT message_id, original_message_id, status FROM payment_status")
	if err != nil {
		http.Error(w, "Failed to retrieve payment status messages", http.StatusInternalServerError)
		return
	}
	defer rows.Close()

	var tableRows strings.Builder
	for rows.Next() {
		var paymentStatus PaymentStatus
		err := rows.Scan(&paymentStatus.MessageID, &paymentStatus.OriginalMessage.MessageID, &paymentStatus.Status)
		if err != nil {
			http.Error(w, "Failed to retrieve payment status messages", http.StatusInternalServerError)
			return
		}

		tableRows.WriteString("<tr>")
		tableRows.WriteString(fmt.Sprintf("<td class=\"px-4 py-2\">%s</td>", paymentStatus.MessageID))
		tableRows.WriteString(fmt.Sprintf("<td class=\"px-4 py-2\">%s</td>", paymentStatus.OriginalMessage.MessageID))
		tableRows.WriteString(fmt.Sprintf("<td class=\"px-4 py-2\">%s</td>", paymentStatus.Status))
		tableRows.WriteString("</tr>")
	}

	w.Header().Set("Content-Type", "text/html")
	w.Write([]byte(tableRows.String()))
}
```

and the fastapi one

```python
from fastapi import FastAPI, Request
import pandas as pd
from fastapi.responses import HTMLResponse
import json
from collections import defaultdict




app = FastAPI()

analytics_results = {}  # Global variable to store the analytics results

@app.post("/pacs008-analytics")
async def pacs008_analytics(request: Request):
    pacs008_data = await request.json()
    print(f"the data is: {pacs008_data}")

    if not pacs008_data:
        return analytics_results

    transactions = []

    for payment in pacs008_data:
        for tx in payment['FIToFICstmrCdtTrf']['CdtTrfTxInf']:
            amt = tx['Amt']['InstdAmt']
            transaction = defaultdict(str)
            transaction['Amount'] = float(amt[''])
            transaction['Currency'] = amt['Ccy']
            transactions.append(transaction)

    if not transactions:
        return analytics_results

    pacs008_df = pd.DataFrame(transactions)

    total_amount = analytics_results.get('total_amount', 0) + pacs008_df[pacs008_df['Currency'] == 'EUR']['Amount'].sum()
    currency_counts = analytics_results.get('currency_counts', {})
    for currency, count in pacs008_df['Currency'].value_counts().items():
        currency_counts[currency] = currency_counts.get(currency, 0) + count
    max_amount = max(analytics_results.get('max_amount', 0), pacs008_df[pacs008_df['Currency'] == 'EUR']['Amount'].max())
    min_amount = min(analytics_results.get('min_amount', float('inf')), pacs008_df[pacs008_df['Currency'] == 'EUR']['Amount'].min())

    analytics_results['total_amount'] = total_amount
    analytics_results['currency_counts'] = currency_counts
    analytics_results['max_amount'] = max_amount
    analytics_results['min_amount'] = min_amount

    return analytics_results

@app.get("/", response_class=HTMLResponse)
def analytics_report():
    total_amount = analytics_results.get('total_amount', 0)
    currency_counts = analytics_results.get('currency_counts', {})
    max_amount = analytics_results.get('max_amount', 0)
    min_amount = analytics_results.get('min_amount', 0)

    html_content = f"""
    <html>
    <head>
    <title>PACS.008 Analytics</title>
    </head>
    <body>
    <h1>PACS.008 Analytics Results</h1>
    <p>Total Amount: {total_amount}</p>
    <p>Maximum Amount: {max_amount}</p>
    <p>Minimum Amount: {min_amount}</p>
    <h2>Currency Counts:</h2>
    <ul>
    {''.join(f'<li>{currency}: {count}</li>' for currency, count in currency_counts.items())}
    </ul>
    </body>
    </html>
    """
    return HTMLResponse(content=html_content, status_code=200)


```


Those are both are highly experimental systems and need more refactoring and testing.


## Missing features

For the interest of time and we needed to make a tradeoff between delivering a working demo in favor of extensive unit testing, we are missing some vital components:
- [x] make the project dockerized
- [ ] unit testing especially for the core processing logic, such as the pacs008-processor. In the `dev` branch, we made a custom `Processor` class to parse and prepare pacs008 messages for other endpoints. But it is not availble in `main` yet
- [ ] more _integration tests_ as we are expanding this dummy platform, it is very obvious we are bound to interact with external services those ought to be stubbed and mocked so that we can deterministically be confident about our system state