# Telemetry in Taskmanager

The TaskManager application is equipped with OpenTelemetry capabilities.
That means that it supports the use of an OpenTelemetry java agent, even though it is not manually instrumented.

## Using metrics for autoscaling

To effectively use resources it is recommended to use automatic scaling of workers based on work load.
When load is low the system can be scaled down and when demand increases the system can be scaled up.
Metrics are reported by a 1 minute time frame.
Scaling can be triggered by monitoring metrics and when certain conditions are met for several minutes it can trigger scaling up or down.
Scaling up or down is typically done by increasing/decreasing the number of workers by a preset amount.
Setting the time span for scaling to low can result in erratic behavior either reducing performance because tasks are constantly redone
or will trigger blocks by the service provider.

> [!NOTE]
> There is no mechanism in place to control which workers to scaling down if they are doing work or are idle.
> This means scaling down means workers are arbitrary shutdown if they are running or not.
> This is no problem as the task will just be put on the queue.
> But when task run long this means all that work will be redone.
> Therefore it is recommended to use a scale down strategy which only scales down when no works is done for workers that handle long running tasks.

To control the scaling there are several metrics available both in the TaskManager as well as based on RabbitMQ metrics.
The metrics from the TaskManager a more fine-grained as they calculate averages per minute.
The metrics from RabbitMQ are snap shots,
require a bit more work to get the same information as the TaskManager metrics.
But can handle situations where the TaskManager restarts, see Note.

> [!NOTE]
> When the TaskManager is restarted it looses the state of the task running.
> This is no issue for the operation of the system because that doesn't depend on that information.
> However, it can affect auto scaling.
> Because after restart the TaskManager will report as if there are no tasks running.
> An autoscaling depending on these metrics might decide to scale down.
> This is not a problem, except if there are long running tasks these will be restarted,
> which might be inconvenient.

### Scaling strategies

There are different strategies to use for auto scaling.
Here 2 possible strategies are described.

#### Scale based on load

Scaling based on load means if the system reaches a certain percentage more workers are scaled up.
And if the percentage falls below another percentage workers are scaled down.
Scaling based on the Taskmanager the `aer.taskmanager.work.load` metric is to be used.

It is also possible to scale based on the RabbitMQ metrics.
But that would require some additional calculation to get the load value.
To calculate the load use the following formule:
((`rabbitmq_detailed_queue_messages` - `rabbitmq_detailed_queue_messages_ready`) / `rabbitmq_detailed_queue_messages_consumers`) * 100.
The metrics the attribute value `aerius.worker.<worker type>` of the `rabbitmq_queue` should be used for this.

Scaling on percentage works best when scaling percentages are close to 100%
or the total number of workers is that can be scaled up to is not that high (below 100).
Because with the higher the amount of possible workers the higher the margin becomes.
For example if the scale down percentage is 70%,
and there are 300 workers running it won't scale down until there are less than 210 running.
This could mean 90 workers could be sitting idle for some time before they are scaled down.

#### Scale based on the amount of idle workers

Scaling based on idle workers means if there are less than a certain amount for idle workers the system scales up.
And if the amount of idle workers exceeds a certain value the system scales down.

To scale based on the RabbitMQ metrics the following calculation must be used.
To calculate the load use the following formule:
`rabbitmq_detailed_queue_messages_consumers`- (`rabbitmq_detailed_queue_messages` - `rabbitmq_detailed_queue_messages_ready`)
The metrics the attribute value `aerius.worker.<worker type>` of the `rabbitmq_queue` should be used for this.

## Metrics

### TaskManager metrics

The TaskManager defines a few custom metrics for OpenTelemetry to capture.
These metrics are all defined with the `nl.aerius.TaskManager` instrumentation scope.

| metric name                                         | type      | description                                                          |
|-----------------------------------------------------|-----------|----------------------------------------------------------------------|
| `aer.taskmanager.work.load`<sup>1</sup>             | gauge     | Percentage of workers occupied.                                      |
| `aer.taskmanager.worker_size`<sup>1</sup>           | gauge     | The sum of idle workers + occupied workers.                          |
| `aer.taskmanager.current_worker_size`<sup>1</sup>   | gauge     | The number of workers based on what RabbitMQ reports.                |
| `aer.taskmanager.running_worker_size`<sup>1</sup>   | gauge     | The number of workers that are occupied.                             |
| `aer.taskmanager.running_client_size`<sup>2</sup>   | gauge     | The number of workers that are occupied for a specific client queue. |
| `aer.taskmanager.dispatched`<sup>1</sup>            | histogram | The number of tasks dispatched.                                      |
| `aer.taskmanager.dispatched.wait`<sup>1</sup>       | histogram | The average wait time of tasks dispatched.                           |
| `aer.taskmanager.dispatched.queue`<sup>2</sup>      | histogram | The number of tasks dispatched per client queue.                     |
| `aer.taskmanager.dispatched.queue.wait`<sup>2</sup> | histogram | The average wait time of tasks dispatched per client queue.          |

The workers have different attributes to distinguish specific metrics.
* <sup>1</sup> have attribute `worker_type`.
* <sup>2</sup> have attribute `worker_type` and `queue_name`.

`worker_type` is the type of worker, e.g. `ops`.
`queue_name` is the originating queue the task initially was put on, e.g. `...calculator_ui_small`.

> [!NOTE]
> The metrics in the TaskManager operate on a time frame of 1 minute.
> The size and load metrics calculate a weighted average within that time frame taking into account the time span of each measure point within the time frame.

### RabbitMQ metrics

| metric name                                         | type  | description                                                        |
|-----------------------------------------------------|-------|--------------------------------------------------------------------|
| `rabbitmq_detailed_queue_messages`                  | gauge | Total number of messages on the queue, both picked up and waiting. |
| `rabbitmq_detailed_queue_messages_ready`            | gauge | Number of messages waiting to be picked up.                        |
| `rabbitmq_detailed_queue_messages_consumers`        | gauge | Total number of workers available.                                 |

Each of those metrics have an attribute `rabbitmq_queue`.
There are 2 groups of metrics that are values of the attribute.
First the queues from the TaskManager to the worker.
These have the pattern `aerius.worker.<worker type>`.
For scaling these are relevant.
Second the queues with work towards the TaskManager.
These have the pattern `aerius.<worker type>.<job type>`.

It will require some arithmetic to calculate the amount of tasks  on the worker or idle workers because the metrics don't give that information directly.
Because each worker process represents 1 RabbitMQ consumer the metric `rabbitmq_detailed_queue_messages_consumers` can be used to measure the amount of workers available.

## Traces

When using the OpenTelemetry java agent, use of the RabbitMQ library within TaskManager should ensure that spans are created on task pickup/delivery.
Taskmanager will try to keep traces per task intact as much as possible, for instance when switching threads.
Taskmanager does not explicitly defines spans itself currently, it relies on the automatic spans created when using the java agent.

