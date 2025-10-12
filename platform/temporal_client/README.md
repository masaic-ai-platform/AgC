# Temporal Client

A standalone Temporal worker client that can be run as a JAR file to connect to Temporal Cloud and execute activities with hot reload capabilities.

## Building the JAR

To build the fat JAR with all dependencies:

```bash
./gradlew :temporal_client:fatJar
```

This will create a JAR file at: `temporal_client/build/libs/temporal_client-0.6.0-dev-all.jar`

## Running the JAR

Simply navigate to the `temporal_client` directory and run:

```bash
cd temporal_client
./run.sh
```

The application will:
- Connect to the configured Temporal Cloud instance
- Create a `config/worker-config.json` file on first run (if running from JAR)
- Start workers based on the configuration
- Enable hot reload for configuration changes

## Configuration

### Worker Configuration

When running from JAR, the configuration file is located at `config/worker-config.json`:

```json
{
  "profileid": "user_yWrOnKu6n",
  "queues": [
    {"name": "add_two_number_new"}
  ]
}
```

**Queue Naming**: Queues are automatically prefixed with the profile ID. For example, with profile `"user_yWrOnKu6n"` and queue `"add_two_number_new"`, the actual queue name will be `"user_yWrOnKu6n.add_two_number_new"`.

### Hot Reload

The application supports hot reload of the configuration file. Simply edit `config/worker-config.json` and the application will:

1. Detect the file change automatically
2. Shutdown existing workers gracefully
3. Load the new configuration
4. Create new workers with the updated queues
5. Start the new workers

This allows you to add/remove queues without restarting the application.

## Features

- ✅ **Configuration-based**: JSON configuration for queues and workers
- ✅ **Hot Reload**: Update configuration without restarting
- ✅ **Auto-Config Extraction**: Automatically creates config file on first run
- ✅ **Proper Logging**: Structured logging with SLF4J and Logback
- ✅ **Multiple Queues**: Support for multiple task queues
- ✅ **Temporal Cloud**: Pre-configured connection to Temporal Cloud
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
- Verify Temporal Cloud credentials and network connectivity
- For hot reload issues, check file permissions on `config/worker-config.json`
- Queue names are automatically prefixed with profile ID (e.g., `user_yWrOnKu6n.add_two_number_new`)
- If config file is not created, check write permissions in the directory
