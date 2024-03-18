# pacs.008 Payment Processing System

## Introduction

I am proud to present the pacs.008 Payment Processing System, a comprehensive solution for handling and processing pacs.008 payment messages. This system demonstrates how one can design and implement scalable, distributed, and highly efficient payment processing architectures.

The idea is that I have been mostly doing iso8583 and other in-house apis, but i never really had the chance to get my hands dirty when it comes to iso20022 -- this a good starting point for me. Also, <https://adonese.sd/book> touched on many payment topics but still didn't manage to have any input on iso20022 this is the time.

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


## Missing features

For the interest of time and we needed to make a tradeoff between delivering a working demo in favor of extensive unit testing, we are missing some vital components:
- [x] make the project dockerized
- [ ] unit testing especially for the core processing logic, such as the pacs008-processor. In the `dev` branch, we made a custom `Processor` class to parse and prepare pacs008 messages for other endpoints. But it is not availble in `main` yet
- [ ] more _integration tests_ as we are expanding this dummy platform, it is very obvious we are bound to interact with external services those ought to be stubbed and mocked so that we can deterministically be confident about our system state