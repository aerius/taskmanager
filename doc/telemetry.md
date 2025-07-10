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

| metric name                             | type      | description                                                          |
|-----------------------------------------|-----------|----------------------------------------------------------------------|
| `aer.taskmanager.worker_size`<sup>1</sup>           | gauge     | The number of workers that are configured according to Taskmanager.  |
| `aer.taskmanager.current_worker_size`<sup>1</sup>   | gauge     | The number of workers that are current in Taskmanager.               |
| `aer.taskmanager.running_worker_size`<sup>1</sup>   | gauge     | The number of workers that are occupied in Taskmanager.              |
| `aer.taskmanager.running_client_size`<sup>2</sup>   | gauge     | The number of workers that are occupied for a specific client queue. |
| `aer.taskmanager.dispatched`<sup>1</sup>            | histogram | The number of tasks dispatched.                                      |
| `aer.taskmanager.dispatched.wait`<sup>1</sup>       | histogram | The average wait time of tasks dispatched.                           |
| `aer.taskmanager.dispatched.queue`<sup>2</sup>      | histogram | The number of tasks dispatched per client queue.                     |
| `aer.taskmanager.dispatched.queue.wait`<sup>2</sup> | histogram | The average wait time of tasks dispatched per client queue.          |
| `aer.taskmanager.work.load`<sup>1</sup>             | gauge     | Percentage of workers used in the timeframe (1 minute).              |

The workers have different attributes to distinguish specific metrics.
* <sup>1</sup> have attribute `worker_type`.
* <sup>2</sup> have attribute `worker_type` and `queue_name`.

`worker_type` is the type of worker, e.g. `ops`.
`queue_name` is the originating queue the task initially was put on, e.g. `...calculator_ui_small`.
