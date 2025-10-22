# Telco Revenue Retention - Existing System In Telcos

This use case demonstrate how with few lines of code Revenue Retention System in telcos can be made agent ready using AgC **client-side tools**

## 🎯 Overview

This application showcases a real-world enterprise use case where an AI agent helps telecom operators identify at-risk customers and apply targeted retention strategies. It demonstrates the power of **client-side tool execution** where business logic and sensitive
data remain on the customer's infrastructure while leveraging AgC's orchestration loop.

### Key Features
- **Low Cost Of Change** - No change in existing application required.
- 🔒 **Client-Side Tool Execution** - Call existing cohort selection and apply retention services within execution of client side tool.
- **AI AgC Loop** - Deep AI orchestration is managed by AgC and not off loaded to existing stack in enterprises
---

## 🚀 Getting Started With Demo App

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
   private static final String B64 =  System.getenv("AGC_CREDS");
   ```

   The `B64` variable should contain your encrypted AgC credentials in OpenSSL salted format. You'll receive this from the AgC platform while selecting AutonomousRevenueRetentionAgent .

   **Credential Format:**
   ```json
   {
     "target": "temporal.cloud.endpoint:7233",
     "namespace": "your-namespace",
     "apiKey": "your-api-key",
     "userId": "agc-user-id"
   }
   ```
4. **Run the project**
   ```bash
   ./gradlew run
   ```
   The application will run continuously, polling for tool execution requests from the AgC platform.

---

## 🧪 API-only Java Example (no UI)

Use this mode to call the client-side tools purely via API using a standalone Java main. This runs a single request/response flow using the OpenAI Java SDK against the AgC gateway.

### Main class
- `usecases.AgCLoopWithTelcoRevenueRetentionExample`

### What it does
- Validates inputs, calls `select_retention_cohort`, then (based on results) prepares for a single `apply_retention_actions` call.
- Uses `OPENAI_API_KEY` and targets the AgC gateway at `http://localhost:6644/v1`.
- Default user query is: "Find high-value customers with repeated payment failures and unresolved complaints, and recommend actions to retain them."

### Required environment
Set the following before running:
```bash
  export OPENAI_API_KEY="<your-openai-api-key>"
  export AGC_CREDS="<your-encrypted-agc-creds>"
```

### How to run
- Option A (IDE): Run the main class `usecases.AgCLoopWithTelcoRevenueRetentionExample` directly from your IDE.
- Option B (temporary Gradle switch): Change the run target, then revert when done.

1) Edit `build.gradle.kts` temporarily:
```kotlin
application {
    mainClass.set("usecases.AgCLoopWithTelcoRevenueRetentionExample")
}
```
2) Build and run:
```bash
  ./gradlew clean run
```
3) Revert `mainClass` back to `ApplicationStart` when finished.

Output will print the model response JSON for the API-based flow.



###  Code Snapshot

Minimal entry to run the API-only flow:

```java
public class AgCLoopWithTelcoRevenueRetentionExample {
    private static final String API_KEY = System.getenv("OPENAI_API_KEY");
    private static final String BASE_URL = "http://localhost:6644/v1";
    private static final String MODEL = "openai@gpt-4.1-mini";
    private static final String B64 = System.getenv("AGC_CREDS");

    public static void main(String[] args) throws Exception {
        Util.decrypt(B64);
        OpenAIClient client = OpenAIOkHttpClient.builder()
            .apiKey(API_KEY)
            .baseUrl(BASE_URL)
            .headers(Headers.builder().put("x-user-id", Util.getCreds("userId")).build())
            .build();

        ResponseCreateParams params = ResponseCreateParams.builder()
            .instructions("...system prompt...")
            .model(MODEL)
            .input("Find high-value customers ...")
            .tools(List.of(/* select_retention_cohort, apply_retention_actions */))
            .build();

        Response resp = client.responses().create(params);
        System.out.println(resp.output().get(0)._json());
    }
}
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
│   ├── ApplicationStart.java                 # Main entry point & tools boot up
│   │
│   ├── common/
│   │   ├── AgCClientSideTool.java           # Client-side tool interface of AgC Loop
│   │   ├── ToolRequest.java                 # Tool request wrapper
│   │   └── LoopContextInfo.java             # Context information for loops
│   │
│   ├── usecases/                            # API-only (no UI) examples
│   │   └── AgCLoopWithTelcoRevenueRetentionExample.java  # Pure API orchestration main
│   │
│   ├── tools/                                # Client-Side Tools Layer
│   │   ├── SelectRetentionCohort.java       # Cohort selection tool
│   │   └── ApplyRetentionActions.java       # Retention action tool
│   │
│   ├── core/
│   │   ├── service/                          # Existing Business Logic Layer
│   │   │   ├── CohortSelectorService.java   # Cohort selection business logic
│   │   │   └── RetentionActionService.java  # Retention action business logic
│   │   │
│   │   ├── repository/                       # Existing Data Access Layer
│   │   │   ├── CustomerDataRepository.java  # Customer data access
│   │   │   └── RetentionActionRepository.java # Action data access
│   │   │
│   │   └── records/                          # Existing Domain Models
│   │       ├── Customer.java                # Customer entity
│   │       └── Payment.java                 # Payment failure entity
│   │
│   └── data/                                 # Existing DTOs & Request/Response Models
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
1. Call existing cohortSelectorService within the scope of tool
```java
    public String executeTool(ToolRequest request) {
      RetentionCohortRequest p = MAPPER.readValue(request.getArguments(), RetentionCohortRequest.class);
      List<RetentionCohortResponse.CohortItem> cohort = cohortSelectorService.select(p);
      RetentionCohortResponse response = new RetentionCohortResponse(cohort);
      return MAPPER.writeValueAsString(response);
    }
```
2. OpenAI compliant tool schema
```json
{
  "type": "function",
  "name": "select_retention_cohort",
  "description": "Returns a redacted list of customers who meet specified retention-risk criteria (revenue threshold, recent payment failures, unresolved complaints",
  "parameters": {
    "type": "object",
    "properties": {
      "min_revenue": {
        "type": "number",
        "default": 100000
      },
      "region": {
        "type": "string",
        "default": "IN"
      },
      "lookback_days": {
        "type": "number",
        "default": 2
      },
      "min_payment_failures": {
        "type": "number",
        "default": 3
      },
      "min_open_complaints": {
        "type": "number",
        "default": 2
      },
      "execution_specs": {
        "type": "object",
        "properties": {
          "type": {
            "type": "string",
            "enum": [
              "client_side"
            ]
          }
        }
      }
    },
    "required": [
      "min_revenue",
      "region",
      "lookback_days",
      "min_payment_failures",
      "min_open_complaints"
    ],
    "additionalProperties": false
  },
  "strict": true
}
```

### Tool 2: ApplyRetentionActions (OpenAI compliant schema).
1. Call existing retentionActionService within the scope of tool
```java
    public String executeTool(ToolRequest request) {
        ApplyRetentionRequest actionRequest = MAPPER.readValue(request.getArguments(), ApplyRetentionRequest.class);
        ApplyRetentionResponse response = retentionActionService.applyRetentionActions(actionRequest);
   return MAPPER.writeValueAsString(response);
}
```

2. OpenAI compliant tool schema
```json
{
  "type": "function",
  "name": "apply_retention_actions",
  "description": "Executes a single retention action for a batch of customers identified only by customer_id_hash.",
  "parameters": {
    "type": "object",
    "properties": {
      "action": {
        "type": "string",
        "enum": [
          "offer_discount",
          "assign_agent",
          "schedule_callback",
          "waive_fee"
        ]
      },
      "customers": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "customer_id_hash": {
              "type": "string"
            }
          }
        }
      },
      "execution_specs": {
        "type": "object",
        "properties": {
          "type": {
            "type": "string",
            "enum": [
              "client_side"
            ]
          }
        }
      }
    },
    "required": [
      "action",
      "customers"
    ],
    "additionalProperties": false
  },
  "strict": true
}
```

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

### Data Privacy
- Customer IDs are SHA-256 hashed before transmission
- Sensitive data never sent out of compliant system
- All PII processing happens inside secure zone.

---


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
