# Telco Revenue Retention - Enterprise AgC Application

A production-ready enterprise application demonstrating **client-side tools** with AgC (Agentic Cloud) platform for telecom customer retention using AI-driven cohort selection and retention action management.

## 🎯 Overview

This application showcases a real-world enterprise use case where an AI agent helps telecom operators identify at-risk customers and apply targeted retention strategies. It demonstrates the power of **client-side tool execution** where business logic and sensitive data remain on the customer's infrastructure while leveraging AgC's orchestration capabilities.

### Key Features

- 🔒 **Client-Side Tool Execution** - Sensitive customer data never leaves your infrastructure
- 🎯 **AI-Driven Cohort Selection** - Intelligent risk scoring for customer retention
- ⚡ **Real-Time Processing** - Queue-based execution
- 🏢 **Enterprise-Grade** - Production-ready architecture with proper layering
- 📊 **Risk Analytics** - Multi-factor risk scoring based on payment failures and complaints
---

## 🚀 Getting Started

### Prerequisites

- **Java 17** or higher
- **Gradle** (included via wrapper)
- **AgC Platform Account** with API credentials

### Installation

1. **Clone the repository**
   ```bash
   cd platform/examples/telco-revenue-retention
   ```

2. **Verify Java installation**
   ```bash
   java -version
   # Should show Java 17 or higher
   ```

3. **Configure credentials**
   Open `ApplicationStart.java` and update the credentials:
   
   ```java
   private static final String B64 = "<ENTER_CREDS_HERE>";
   ```
   
   The `B64` variable should contain your encrypted AgC credentials in OpenSSL salted format. You'll receive this from the AgC platform while selecting AutonomousRevenueRetentionAgent .

   **Credential Format:**
   ```json
   {
     "target": "temporal.cloud.endpoint:7233",
     "namespace": "your-namespace",
     "apiKey": "your-api-key",
     "userId": "your-user-id"
   }
   ```
4. **Run the project**
   ```bash
   ./gradlew run
   ```
   The application will run continuously, polling for tool execution requests from the AgC platform.

## 🏗️ Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        AgC Platform (Cloud)                      │
│  ┌────────────────────────────────────────────────────────┐    │
│  │              AI Agent Orchestration Layer              │    │
│  │  • LLM-based decision making                           │    │
│  │  • Workflow coordination                               │    │
│  │  • Tool routing & scheduling                           │    │
│  └────────────────────────────────────────────────────────┘    │
│                            │                                     │
│                            │ Queue Management & Message Broker   │
│                            │ (Push-based communication)          │
│                            │                                     │
│  ┌────────────────────────────────────────────────────────┐    │
│  │              Message Queues                           │    │
│  │  ┌─────────────────┐    ┌─────────────────────────┐   │    │
│  │  │  Tool Queue 1   │    │    Tool Queue 2         │   │    │
│  │  │ select_retention│    │ apply_retention_actions │   │    │
│  │  │ _cohort         │    │                         │   │    │
│  │  └─────────────────┘    └─────────────────────────┘   │    │
│  └────────────────────────────────────────────────────────┘    │
│                            │                                     │
│                            │ gRPC/HTTPS (Workflow Protocol)     │
│                            │ (Pull-based polling)               │
└────────────────────────────┼─────────────────────────────────────┘
                             │
                   ┌─────────▼──────────┐
                   │  Secure Connection │
                   │  (Bearer Auth)     │
                   └─────────┬──────────┘
                             │
┌────────────────────────────▼─────────────────────────────────────┐
│              Customer Infrastructure (On-Premise/VPC)            │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │           Telco Revenue Retention Application            │    │
│  │                                                          │  │
│  │  ┌──────────────────────────────────────────────────┐    │  │
│  │  │          Worker Factory                          │    │  │
│  │  │     (Listens to Queues & Polls for Tasks)           │    │ │
│  │  │                                                     │    │ │
│  │  │  ┌───────────────┐      ┌──────────────────┐    │   │  │
│  │  │  │ Worker 1      │      │  Worker 2        │    │   │  │
│  │  │  │ (Cohort Tool) │      │ (Retention Tool) │    │   │  │
│  │  │  │               │      │                  │    │   │  │
│  │  │  │ Polls Queue 1 │      │ Polls Queue 2    │    │   │  │
│  │  │  └───────┬───────┘      └────────┬─────────┘    │   │  │
│  │  └──────────┼──────────────────────┼──────────────┘   │  │
│  │             │                       │                   │  │
│  │  ┌──────────▼───────────────────────▼──────────────┐  │  │
│  │  │          Client-Side Tools Layer               │  │  │
│  │  │  (Processes Tasks from Queues)                 │  │  │
│  │  │                                                  │  │  │
│  │  │  ┌───────────────────┐  ┌────────────────────┐ │  │  │
│  │  │  │SelectRetention    │  │ApplyRetention      │ │  │  │
│  │  │  │Cohort             │  │Actions             │ │  │  │
│  │  │  │                   │  │                    │ │  │  │
│  │  │  │• Risk scoring     │  │• Action execution  │ │  │  │
│  │  │  │• Cohort filtering │  │• Status tracking   │ │  │  │
│  │  │  │• Data hashing     │  │• Validation        │ │  │  │
│  │  │  └─────────┬─────────┘  └────────┬───────────┘ │  │  │
│  │  └────────────┼──────────────────────┼─────────────┘  │  │
│  │               │                      │                 │  │
│  │  ┌────────────▼──────────────────────▼─────────────┐  │  │
│  │  │            Service Layer                        │  │  │
│  │  │                                                  │  │  │
│  │  │  ┌──────────────────┐  ┌─────────────────────┐ │  │  │
│  │  │  │CohortSelector    │  │RetentionAction      │ │  │  │
│  │  │  │Service           │  │Service              │ │  │  │
│  │  │  │                  │  │                     │ │  │  │
│  │  │  │• Business logic  │  │• Request validation │ │  │  │
│  │  │  │• Risk calculation│  │• Summary generation │ │  │  │
│  │  │  │• Sorting/ranking │  │• Error handling     │ │  │  │
│  │  │  └────────┬─────────┘  └──────────┬──────────┘ │  │  │
│  │  └───────────┼────────────────────────┼────────────┘  │  │
│  │              │                        │                │  │
│  │  ┌───────────▼────────────────────────▼────────────┐  │  │
│  │  │          Repository Layer                       │  │  │
│  │  │                                                  │  │  │
│  │  │  ┌──────────────────┐  ┌─────────────────────┐ │  │  │
│  │  │  │CustomerData      │  │RetentionAction      │ │  │  │
│  │  │  │Repository        │  │Repository           │ │  │  │
│  │  │  │                  │  │                     │ │  │  │
│  │  │  │• Data access     │  │• Action processing  │ │  │  │
│  │  │  │• Mock data gen   │  │• Result tracking    │ │  │  │
│  │  │  └────────┬─────────┘  └─────────────────────┘ │  │  │
│  │  └───────────┼──────────────────────────────────────┘  │  │
│  │              │                                          │  │
│  │  ┌───────────▼──────────────────────────────────────┐  │  │
│  │  │          Data Layer                              │  │  │
│  │  │                                                   │  │  │
│  │  │  • Customer Records (ID, Region, Revenue)        │  │  │
│  │  │  • Payment Failures History                      │  │  │
│  │  │  • Complaint Data                                │  │  │
│  │  │  • Retention Actions & Results                   │  │  │
│  │  └──────────────────────────────────────────────────┘  │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Component Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                    Component Hierarchy                        │
└──────────────────────────────────────────────────────────────┘

ApplicationStart (Main Entry Point)
    │
    ├─── Credential Management (Decryption & Authentication)
    │
    ├─── Temporal Workflow Client Setup
    │    ├─── gRPC Connection (HTTPS enabled)
    │    ├─── Bearer Token Authentication
    │    └─── Namespace Configuration
    │
    └─── Worker Factory (Tool Registration)
         │
         ├─── Worker 1: select_retention_cohort
         │    └─── SelectRetentionCohort (AgCClientSideTool)
         │         └─── CohortSelectorService
         │              └─── CustomerDataRepository
         │
         └─── Worker 2: apply_retention_actions
              └─── ApplyRetentionActions (AgCClientSideTool)
                   └─── RetentionActionService
                        └─── RetentionActionRepository
```

### Data Flow Diagram

```
┌────────────┐         ┌──────────────┐         ┌─────────────────┐
│ AI Agent   │         │ AgC Platform │         │  Customer App   │
│ (LLM)      │         │  + Queues    │         │  (This Code)    │
└─────┬──────┘         └──────┬───────┘         └────────┬────────┘
      │                       │                          │
      │  1. Analyze context   │                          │
      │────────────────────>  │                          │
      │                       │                          │
      │  2. Push task to      │                          │
      │     queue (select_    │                          │
      │     retention_cohort) │                          │
      │────────────────────>  │                          │
      │                       │                          │
      │                       │  3. Worker polls queue   │
      │                       │     and picks up task    │
      │                       │─────────────────────────>│
      │                       │                          │
      │                       │                          │  4. Execute
      │                       │                          │     tool logic
      │                       │                          │     locally
      │                       │                          │     (business
      │                       │                          │      logic +
      │                       │                          │      data access)
      │                       │                          │
      │                       │  5. Push results back    │
      │                       │     to result queue      │
      │                       │<─────────────────────────│
      │  6. Process results   │                          │
      │     from result queue │                          │
      │<────────────────────  │                          │
      │                       │                          │
      │  7. Push next task    │                          │
      │     to queue          │                          │
      │     (apply_retention_ │                          │
      │     actions)          │                          │
      │────────────────────>  │                          │
      │                       │                          │
      │                       │  8. Worker polls queue   │
      │                       │     and picks up task    │
      │                       │─────────────────────────>│
      │                       │                          │
      │                       │  9. Execute actions &    │
      │                       │     push results         │
      │                       │<─────────────────────────│
      │  10. Process final    │                          │
      │      results          │                          │
      │<────────────────────  │                          │
      │                       │                          │
```

### Queue Communication Pattern

```
┌─────────────────────────────────────────────────────────────────┐
│                    AgC Platform (Cloud)                         │
│                                                                 │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────────┐    │
│  │ AI Agent    │    │ Queue       │    │ Result Queue    │    │
│  │ (LLM)       │───▶│ Manager     │───▶│ Manager         │    │
│  └─────────────┘    └─────────────┘    └─────────────────┘    │
│         │                   │                    ▲             │
│         │                   │                    │             │
│         │                   ▼                    │             │
│         │            ┌─────────────┐             │             │
│         │            │ Tool Queues │             │             │
│         │            │             │             │             │
│         │            │ ┌─────────┐ │             │             │
│         │            │ │select_  │ │             │             │
│         │            │ │retention│ │             │             │
│         │            │ │_cohort  │ │             │             │
│         │            │ └─────────┘ │             │             │
│         │            │             │             │             │
│         │            │ ┌─────────┐ │             │             │
│         │            │ │apply_   │ │             │             │
│         │            │ │retention│ │             │             │
│         │            │ │_actions │ │             │             │
│         │            │ └─────────┘ │             │             │
│         │            └─────────────┘             │             │
│         │                   │                    │             │
│         └───────────────────┼────────────────────┘             │
└─────────────────────────────┼─────────────────────────────────┘
                              │ gRPC/HTTPS Polling
                              │
┌─────────────────────────────▼─────────────────────────────────┐
│              Customer Infrastructure (On-Premise)             │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │               Worker Factory                            │  │
│  │                                                         │  │
│  │  ┌─────────────┐              ┌─────────────┐          │  │
│  │  │ Worker 1    │              │ Worker 2    │          │  │
│  │  │ (Cohort)    │              │ (Retention) │          │  │
│  │  │             │              │             │          │  │
│  │  │ Polls Queue │              │ Polls Queue │          │  │
│  │  │ Continuously│              │ Continuously│          │  │
│  │  └─────────────┘              └─────────────┘          │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │              Client-Side Tools                          │  │
│  │  (Process tasks from queues & return results)          │  │
│  └─────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔧 Tech Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Language** | Java 17 | Core application development |
| **Build Tool** | Gradle (Kotlin DSL) | Dependency & build management |
| **AI Integration** | OpenAI Java SDK 2.2.0 | LLM communication |
| **Logging** | SLF4J + Logback | Structured logging |
| **Serialization** | Jackson | JSON processing |
| **Security** | JCE (AES-256) | Credential encryption |

---

## 📦 Project Structure

```
telco-revenue-retention/
│
├── src/main/java/
│   │
│   ├── ApplicationStart.java                 # Main entry point & worker setup
│   │
│   ├── common/
│   │   ├── AgCClientSideTool.java           # Client-side tool interface
│   │   ├── ToolRequest.java                 # Tool request wrapper
│   │   └── LoopContextInfo.java             # Context information for loops
│   │
│   ├── tools/                                # Client-Side Tools Layer
│   │   ├── SelectRetentionCohort.java       # Cohort selection tool
│   │   └── ApplyRetentionActions.java       # Retention action tool
│   │
│   ├── core/
│   │   ├── service/                          # Business Logic Layer
│   │   │   ├── CohortSelectorService.java   # Cohort selection business logic
│   │   │   └── RetentionActionService.java  # Retention action business logic
│   │   │
│   │   ├── repository/                       # Data Access Layer
│   │   │   ├── CustomerDataRepository.java  # Customer data access
│   │   │   └── RetentionActionRepository.java # Action data access
│   │   │
│   │   └── records/                          # Domain Models
│   │       ├── Customer.java                # Customer entity
│   │       └── Payment.java                 # Payment failure entity
│   │
│   └── data/                                 # DTOs & Request/Response Models
│       ├── RetentionCohortRequest.java      # Cohort selection request
│       ├── RetentionCohortResponse.java     # Cohort selection response
│       ├── ApplyRetentionRequest.java       # Retention action request
│       └── ApplyRetentionResponse.java      # Retention action response
│
├── src/main/resources/
│   └── logback.xml                          # Logging configuration
│
├── build.gradle.kts                         # Build configuration
├── settings.gradle.kts                      # Project settings
├── gradle.properties                        # Gradle properties
└── gradlew / gradlew.bat                    # Gradle wrapper scripts
```

---

## 🛠️ Client-Side Tools

### Why Client-Side Tools?

Client-side tools enable **secure, enterprise-grade AI applications** by:

1. **Data Privacy** - Sensitive customer data never leaves your infrastructure
2. **Compliance** - Meet regulatory requirements (GDPR, HIPAA, etc.)
3. **Performance** - Reduce latency by processing data locally
4. **Scalability** - Distribute workload across your infrastructure
5. **Control** - Full control over business logic and data access

### Tool 1: SelectRetentionCohort

**Purpose:** Identify at-risk customers based on payment failures, complaints, and revenue.

**Input Parameters:**
```json
{
  "min_revenue": 100000.0,
  "lookback_days": 60,
  "min_payment_failures": 2,
  "min_open_complaints": 1,
  "limit": 500,
  "region": "IN"
}
```

**Risk Scoring Algorithm:**
```
risk_score = 0.6 × (failures_factor) + 0.4 × (complaints_factor)

where:
  failures_factor = min(failures / 3.0, 1.0)
  complaints_factor = min(complaints / 2.0, 1.0)
```

**Output:**
```json
{
  "cohort": [
    {
      "id_hash": "a3b5c7d9",
      "risk_score": 0.850
    }
  ]
}
```

**Business Logic:**
- Filters customers by revenue threshold
- Analyzes payment failures in lookback window
- Calculates multi-factor risk scores
- Ranks by risk (desc), then revenue (desc)
- Returns top N customers with hashed IDs

### Tool 2: ApplyRetentionActions

**Purpose:** Execute retention campaigns on selected customer cohort.

**Input Parameters:**
```json
{
  "action": "assign_agent",
  "customers": [
    {"customer_id_hash": "a3b5c7d9"},
    {"customer_id_hash": "b4c6d8e0"}
  ]
}
```

**Output:**
```json
{
  "results": [
    {
      "customer_id_hash": "a3b5c7d9",
      "action": "assign_agent",
      "status": "created",
      "message": "Retention action created successfully"
    },
     {
        "customer_id_hash": "b4c6d8e0",
        "action": "assign_agent",
        "status": "Failed",
        "message": "Retention action Failed"
     }
  ],
  "summary": {
    "total_processed": 2,
    "successful": 1,
    "failed": 1
  }
}
```

**Business Logic:**
- Validates retention action type
- Processes each customer individually
- Tracks success/failure for each action
- Provides aggregated summary statistics

---

## 🎯 Use Case Example

### Scenario: Telecom Provider Retention Campaign

**Objective:** Reduce customer churn in the enterprise segment

**Workflow:**

1. **AI Agent receives business goal:**
   > "Identify high-value customers at risk of churning and apply appropriate retention offers"

2. **Agent calls `select_retention_cohort`:**
   - Filters customers with revenue > $100k
   - Looks for 2+ payment failures in last 60 days
   - Requires at least 1 open complaint
   - Limits to top 100 highest-risk customers

3. **Agent analyzes results and determines action:**
   - High-risk customers (score > 0.8): 20% premium discount
   - Medium-risk customers (score 0.5-0.8): Priority support upgrade
   - Low-risk customers (score < 0.5): Loyalty points bonus

4. **Agent calls `apply_retention_actions`:**
   - Executes appropriate retention campaign
   - Tracks success/failure for each customer
   - Provides summary for business reporting

5. **Business Impact:**
   - Reduced manual analysis time by 90%
   - Improved retention rate by 15%
   - Increased customer lifetime value
   - Automated compliance with data privacy regulations

---

## 🔐 Security Features

### 1. Data Privacy
- Customer IDs are SHA-256 hashed before transmission
- Sensitive data never sent to cloud
- All PII processing happens on-premise

### 2. Secure Communication
- HTTPS/TLS for all network communication
- Bearer token authentication
- gRPC metadata-based authorization

### 3. Access Control
- User-specific worker queues
- Namespace isolation
- API key-based authentication

---

## 📊 Monitoring & Logging

### Log Levels

The application uses SLF4J with Logback for structured logging:

```xml
<!-- src/main/resources/logback.xml -->
<configuration>
  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>
  
  <root level="info">
    <appender-ref ref="STDOUT" />
  </root>
</configuration>
```

### Key Metrics to Monitor

1. **Tool Execution Time** - Track latency for each tool
2. **Error Rates** - Monitor failed tool executions
3. **Cohort Size** - Track number of customers in each cohort
4. **Action Success Rate** - Monitor retention action effectiveness
5. **Worker Health** - Ensure workers are polling and active

---

## 🎓 Learning Resources

- [AgC Platform Documentation](https://github.com/masaic-ai-platform/AgC)
- [OpenAI Java SDK](https://github.com/openai/openai-java)
---

## 📝 License

This example is part of the AgC platform and follows the same license terms.

---

## 🤝 Support

For questions or issues:
- Open an issue on [GitHub](https://github.com/masaic-ai-platform/AgC/issues)
- Check the main [AgC README](../../README.md)
---

## 🌟 Key Takeaways

This enterprise example demonstrates:

✅ **Production-ready architecture** with proper separation of concerns  
✅ **Client-side tool execution** for data privacy and compliance  
✅ **Real-world business logic** for telecom retention  
✅ **Scalable design** using workflows
✅ **Clean code practices** following enterprise standards  

---

**Built with ❤️ by the AgC Team**

