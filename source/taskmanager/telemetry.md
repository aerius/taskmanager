# Telemetry in Taskmanager

The taskmanager application is equipped with OpenTelemetry capabilities.
That means that it supports the use of an opentelemetry java agent, even though it is not manually instrumented.

## Traces

When using the opentelemetry java agent, use of the RabbitMQ library within taskmanager should ensure that spans are created on task pickup/delivery.
Taskmanager will try to keep traces per task intact as much as possible, for instance when switching threads.
Taskmanager does not explicitly defines spans itself currently, it relies on the automatic spans created when using the java agent.

## Metrics

The taskmanager defines a few custom metrics for OpenTelemetry to capture.
These metrics are all defined with the `nl.aerius.taskmanager` instrumentation scope.

Current metrics are, per configured queue/worker type (through attribute `worker_type`):

- The number of workers that are configured according to Taskmanager, a gauge with the name `aer.taskmanager.worker_size`
- The number of workers that are current in Taskmanager, a gauge with the name `aer.taskmanager.current_worker_size`
- The number of workers that are occupied in Taskmanager, a gauge with the name `aer.taskmanager.running_worker_size`
- The number of tasks dispatched, a counter with the name `aer.taskmanager.dispatched`
