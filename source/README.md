### Taskmanager image

The image requires queue configuration to be available at `/app/queue`.
This can be done by using `volumes` or by extending the image and adding the files to the image.
Queue configuration can be overridden by using an environment variable.

##### ENV variables that are required or set by default for convenience

- `AERIUS_BROKER_HOST`: Defaults to `localhost`.
- `AERIUS_BROKER_PORT`: Defaults to `5672`.
- `AERIUS_BROKER_USERNAME`: Defaults to `aerius`.
- `AERIUS_BROKER_PASSWORD`: Defaults to `aerius`.
- `AERIUS_PRIORITY_TASK_SCHEDULER_{WORKER QUEUE NAME}`: Overrides the queue configuration for a specific worker. Defaults to the configuration in the files.

##### Example build

```shell
docker build -t aerius-taskmanager:latest .
```

##### Example run

```shell
docker run --rm -it --network host \
  -e AERIUS_BROKER_PASSWORD=password \
  -v /my/path/to/config/of/queue:/app/queue \
  aerius-taskmanager:latest
```
