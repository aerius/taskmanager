# Telemetry in Taskmanager

The TaskManager application is equipped with OpenTelemetry capabilities.
That means that it supports the use of an OpenTelemetry java agent, even though it is not manually instrumented.

## Traces

When using the OpenTelemetry java agent, use of the RabbitMQ library within TaskManager should ensure that spans are created on task pickup/delivery.
Taskmanager will try to keep traces per task intact as much as possible, for instance when switching threads.
Taskmanager does not explicitly defines spans itself currently, it relies on the automatic spans created when using the java agent.

## Metrics

The TaskManager defines a few custom metrics for OpenTelemetry to capture.
These metrics are all defined with the `nl.aerius.TaskManager` instrumentation scope.

| metric name                           | type    | description                                                          |
|---------------------------------------|---------|----------------------------------------------------------------------|
| `aer.taskmanager.worker_size`         | gauge   | The number of workers that are configured according to Taskmanager.  |
| `aer.taskmanager.current_worker_size` | gauge   | The number of workers that are current in Taskmanager.               |
| `aer.taskmanager.running_worker_size` | gauge   | The number of workers that are occupied in Taskmanager.              |
| `aer.taskmanager.running_client_size` | gauge   | The number of workers that are occupied for a specific client queue. |
| `aer.taskmanager.dispatched`          | counter | The number of tasks dispatched.                                      |

Current metrics are, per configured queue/worker type (through attribute `worker_type`):
The gauge  metric `aer.taskmanger.running_client_size` also has the attribute `client_queue_name` containing the name of the client queue.
