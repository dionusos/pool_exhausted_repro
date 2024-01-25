# OpenJPA - commons-pool - commons-dbcp deadlock repro

## Overview

When we create a Java application which uses the above dependencies to persist data
into a relational database like PostgreSQL
we may experience deadlock when the database connections are closed from the DB side.

This issue was discovered in a running Oozie 5.2.1 instance which uses
* OpenJPA 2.4.2
* commons-dbcp 1.4
* commons-pool 1.6

However, we experience the same behaviour when we use the latest and greatest of the above:
* OpenJPA 3.2.2
* commons-dbcp2 2.11.0
* commons-pool2 2.12.0


## Repro steps

### Environment

You will need Java 8, Apache Maven 3.6 and Docker.

### PostgreSQL databse

Create a PostgreSQL database using this Docker command

```bash
docker run -d
--name repro-psql \
--hostname repro-psql \
-e "POSTGRES_USER=repro" \
-e "POSTGRES_PASSWORD=repro" \
-e "POSTGRES_DB=repro" \
-p "5433:5432" postgres:12.17-bullseye
```

And then create the necessary bean table

```roomsql
CREATE TABLE public.my_data (
	id bigint NULL,
	table_key varchar NULL,
	value varchar NULL
);
```
like
```shell
docker exec repro-psql /usr/bin/psql \
  -Urepro -c "CREATE TABLE public.my_data (id bigint NULL, table_key varchar NULL, value varchar NULL);"
```

### Execute this repro program

If you want to use OpenJPA2 and commons-pool, commons-dbcp:
```shell
mvn clean install -Popenjpa2,\!openjpa3
```

If you want to use OpenJPA3 and commons-pool2, commons-dbcp2:
```shell
mvn clean install -P\!openjpa2,openjpa3
```

### Kill the connections from the Database

Execute the following SQL command multiple times quickly after each other
to kill all the connections from the repro program.

```roomsql
SELECT pg_terminate_backend(pg_stat_activity.pid)
FROM pg_stat_activity
WHERE application_name = 'PostgreSQL JDBC Driver';
```

You can use this command, but you may need to repeat executing it multiple times:
```shell
for i in {1..100}; do
  docker exec repro-psql /usr/bin/psql \
  -Urepro -c "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity where application_name = 'PostgreSQL JDBC Driver';"
done
```

### Notice

Notice that the repro program stopped executing.
If you create a `jstack` you will see hanging threads like

~~~stacktrace
"pool-2-thread-4" #20 prio=5 os_prio=31 tid=0x00007faf7903b800 nid=0x8603 waiting on condition [0x000000030f3e7000]
   java.lang.Thread.State: WAITING (parking)
	at sun.misc.Unsafe.park(Native Method)
	- parking to wait for  <0x000000066aca8e70> (a java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject)
	at java.util.concurrent.locks.LockSupport.park(LockSupport.java:175)
	at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await(AbstractQueuedSynchronizer.java:2039)
	at org.apache.commons.pool2.impl.LinkedBlockingDeque.takeFirst(LinkedBlockingDeque.java:1324)
	at org.apache.commons.pool2.impl.GenericObjectPool.borrowObject(GenericObjectPool.java:313)
	at org.apache.commons.pool2.impl.GenericObjectPool.borrowObject(GenericObjectPool.java:233)
	at org.apache.commons.dbcp2.PoolingDataSource.getConnection(PoolingDataSource.java:139)
	at org.apache.commons.dbcp2.BasicDataSource.getConnection(BasicDataSource.java:711)
	at org.apache.openjpa.lib.jdbc.DelegatingDataSource.getConnection(DelegatingDataSource.java:136)
	at org.apache.openjpa.lib.jdbc.DecoratingDataSource.getConnection(DecoratingDataSource.java:94)
	at org.apache.openjpa.lib.jdbc.DelegatingDataSource.getConnection(DelegatingDataSource.java:127)
	at org.apache.openjpa.jdbc.schema.DataSourceFactory$DefaultsDataSource.getConnection(DataSourceFactory.java:321)
	at org.apache.openjpa.jdbc.kernel.JDBCStoreManager.connectInternal(JDBCStoreManager.java:1021)
	at org.apache.openjpa.jdbc.kernel.JDBCStoreManager.connect(JDBCStoreManager.java:1006)
	at org.apache.openjpa.jdbc.kernel.JDBCStoreManager.retainConnection(JDBCStoreManager.java:239)
	at org.apache.openjpa.kernel.DelegatingStoreManager.retainConnection(DelegatingStoreManager.java:187)
	at org.apache.openjpa.kernel.BrokerImpl.retainConnection(BrokerImpl.java:4179)
	at org.apache.openjpa.kernel.BrokerImpl.beginStoreManagerTransaction(BrokerImpl.java:1518)
	at org.apache.openjpa.kernel.BrokerImpl.flush(BrokerImpl.java:2267)
	at org.apache.openjpa.kernel.BrokerImpl.flushSafe(BrokerImpl.java:2201)
	at org.apache.openjpa.kernel.BrokerImpl.beforeCompletion(BrokerImpl.java:2118)
	at org.apache.openjpa.kernel.LocalManagedRuntime.commit(LocalManagedRuntime.java:84)
	- locked <0x000000066ef5c070> (a org.apache.openjpa.kernel.LocalManagedRuntime)
	at org.apache.openjpa.kernel.BrokerImpl.commit(BrokerImpl.java:1603)
	at org.apache.openjpa.kernel.DelegatingBroker.commit(DelegatingBroker.java:1035)
	at org.apache.openjpa.persistence.EntityManagerImpl.commit(EntityManagerImpl.java:690)
	at org.apache.dionusos.pool_exhausted_repro.Repro$ReproRunnable.run(Repro.java:56)
	at java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:511)
	at java.util.concurrent.FutureTask.run(FutureTask.java:266)
	at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)
	at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:624)
	at java.lang.Thread.run(Thread.java:750)
~~~
