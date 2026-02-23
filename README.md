# Subscription & Billing Management System (Multi-Tenant SaaS)

A production-style backend system that demonstrates how SaaS platforms manage **tenants, subscriptions, usage-based billing, and asynchronous event processing** using **Spring Boot, Kafka, and OAuth2/OIDC (Keycloak)**.

This project evolves from a synchronous monolith to an **event-driven architecture**, showcasing real-world backend design patterns.

---

## 🧩 Architecture Overview

Client (Postman)
|
v
Subscription Service (Spring Boot)

Org / Plan / Subscription APIs

Billing Engine (Invoices)

OAuth2 Resource Server (Keycloak)
|
| (publish)
v
Kafka Topic (usage-recorded-topic)
|
| (consume)
v
Usage Consumer

Persists usage events

Feeds Billing Engine


---

## 🛠 Tech Stack

- Java 17
- Spring Boot 3.x
- Spring Data JPA (H2 for local, PostgreSQL-ready)
- Apache Kafka
- OAuth2 / OIDC (Keycloak)
- REST APIs
- Docker
- Maven

---

## 🚀 How to Run Locally

### 1️⃣ Start Infra (Kafka + Keycloak)
```bash
docker compose up -d

Keycloak: http://localhost:8081

Kafka: localhost:9092


cd subscription-service
mvn spring-boot:run

http://localhost:8080

POST /organizations
{
  "name": "Acme Corp"
}


Subscribe Organization to Plan
POST /subscriptions
{
  "organizationId": "<UUID>",
  "planType": "BASIC"
}

Record Usage (Async via Kafka)
POST /usage-events
{
  "organizationId": "<UUID>",
  "unitsConsumed": 50
}


Generate Monthly Invoice
GET /invoices/{orgId}?year=2026&month=2



Key Concepts Demonstrated

Multi-tenant SaaS domain modeling

Subscription lifecycle (create, upgrade, deactivate)

Usage-based billing & invoice calculation

Event-driven architecture using Kafka

Asynchronous processing & decoupling

OAuth2/OIDC security with Keycloak

Clean layered architecture

Docker-based local infrastructure



Future Enhancements

Replace H2 with PostgreSQL + Flyway migrations

Add retry + DLQ for Kafka consumers

Implement Saga for subscription & payment workflows

Add Admin UI (React)

Add metrics with Micrometer + Prometheus

Deploy on Kubernetes