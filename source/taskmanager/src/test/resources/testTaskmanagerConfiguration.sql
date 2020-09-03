/**
 * 
 * Unittests voor taskmanager gebruiken een eigen DB (om te voorkomen dat er dingen door elkaar gaan lopen).
 * 
 * Dit SQL bestand bevat de configuratie voor deze tests.
 */

/*
 * Inserts system task_exchanges.
 */
INSERT INTO system.task_exchanges (name) 
	VALUES ('unitTest');

INSERT INTO system.task_exchanges (name) 
	VALUES ('NewTestScheduler');

INSERT INTO system.task_exchanges (name) 
	VALUES ('TestThisConfiguration');	

/*
 * Inserts system task_brokers.
 */
INSERT INTO system.task_brokers (host, port, username, password, virtual_host) 
	VALUES ('localhost', 5672, 'guest', 'guest', '/');

/*
 * Inserts system task_managers.
 */
INSERT INTO system.task_managers (name, description, default_broker_id)
	SELECT 'unitTestTaskManager', 'Unit tests taskmanager instellingen', task_broker_id 
	FROM system.task_brokers 
	WHERE host = 'localhost' AND port = 5672 AND username = 'guest' AND password = 'guest' AND virtual_host = '/' LIMIT 1;

/*
 * Inserts system task_schedulers.
 */
INSERT INTO system.task_schedulers (description, worker_queue_name, task_manager_id, task_exchange_id)
	SELECT 'Scheduler voor unit tests', 'test.worker.queue', task_manager_id, task_exchange_id
	FROM system.task_managers manager, system.task_exchanges exchange
	WHERE manager.name = 'unitTestTaskManager' AND exchange.name = 'unitTest';

/*
 * Inserts system task_scheduler_queues.
 */
-- Unittest queues 
INSERT INTO system.task_scheduler_queues (task_scheduler_id, name, description, priority, capacity_remaining_limit)
	SELECT task_scheduler_id, 'UnitTestQueue1', 'Unit test queue 1', 1, 0
	FROM system.task_schedulers scheduler 
		JOIN system.task_managers manager USING (task_manager_id)
		JOIN system.task_exchanges exchange USING (task_exchange_id)
	WHERE manager.name = 'unitTestTaskManager' AND exchange.name = 'unitTest';

INSERT INTO system.task_scheduler_queues (task_scheduler_id, name, description, priority, capacity_remaining_limit)
	SELECT task_scheduler_id, 'UnitTestQueue2', 'Unit test queue 2', 3, 0
	FROM system.task_schedulers scheduler 
		JOIN system.task_managers manager USING (task_manager_id)
		JOIN system.task_exchanges exchange USING (task_exchange_id)
	WHERE manager.name = 'unitTestTaskManager' AND exchange.name = 'unitTest';
