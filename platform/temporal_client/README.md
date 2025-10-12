# Temporal Client

A standalone Temporal worker client that can be run as a JAR file to connect to Temporal Cloud and execute activities with hot reload capabilities.

## Building the JAR

To build the fat JAR with all dependencies:

```bash
./gradlew :temporal_client:fatJar
```

This will create a JAR file at: `temporal_client/build/libs/temporal_client-0.6.0-dev-all.jar`

## Running the JAR

### Prerequisites

Set the following environment variables:

- `TEMPORAL_TARGET`: Your Temporal Cloud endpoint (e.g., `us-east-1.aws.api.temporal.io:7233`)
- `TEMPORAL_NAMESPACE`: Your Temporal namespace (e.g., `your-namespace.your-account-id`)
- `TEMPORAL_API_KEY`: Your Temporal Cloud API key

### Running

```bash
java -jar temporal_client-0.6.0-dev-all.jar
```

Or use the convenient run script:
```bash
./run.sh [temporal_target] [namespace] [api_key]
```

Or with environment variables inline:

```bash
TEMPORAL_TARGET=us-east-1.aws.api.temporal.io:7233 \
TEMPORAL_NAMESPACE=your-namespace.your-account-id \
TEMPORAL_API_KEY=your-api-key \
java -jar temporal_client-0.6.0-dev-all.jar
```

## Configuration

The worker configuration is defined in `worker-config.json`:

```json
{
  "profileid": "default",
  "queues": [
    {"name": "user_yWrOnKu6n.add_two_number_new"},
    {"name": "math.add_numbers"},
    {"name": "math.multiply_numbers"}
  ]
}
```

**Queue Naming**: Queues are automatically prefixed with the profile ID. For example, with profile `"default"` and queue `"math.add_numbers"`, the actual queue name will be `"default.math.add_numbers"`.

### Hot Reload

The application supports hot reload of the configuration file. When you modify `worker-config.json`, the application will:

1. Detect the file change
2. Shutdown existing workers gracefully
3. Load the new configuration
4. Create new workers with the updated configuration
5. Start the new workers

This allows you to add/remove queues without restarting the application.

## Features

- ✅ **Configuration-based**: JSON configuration for queues and workers
- ✅ **Hot Reload**: Update configuration without restarting
- ✅ **Proper Logging**: Structured logging with SLF4J and Logback
- ✅ **Multiple Queues**: Support for multiple task queues
- ✅ **Temporal Cloud**: Connects to Temporal Cloud with API key authentication
- ✅ **Graceful Shutdown**: Proper cleanup on application shutdown
- ✅ **Production Ready**: Error handling, logging, and monitoring

## Logging

Logs are written to console only. Log levels can be configured in `logback-spring.xml`.

## Development

To add new activities:

1. Create a new activity class implementing the appropriate interface
2. Register it in the `TemporalWorkerFactory.createWorkerForQueue()` method
3. Update the configuration JSON if needed

## Troubleshooting

- Check console logs for detailed error information
- Ensure all environment variables are set correctly
- Verify Temporal Cloud credentials and network connectivity
- For hot reload issues, check file permissions on the configuration file
- Queue names are automatically prefixed with profile ID (e.g., `default.math.add_numbers`)
