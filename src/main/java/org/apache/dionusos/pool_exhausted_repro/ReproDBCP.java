package org.apache.dionusos.pool_exhausted_repro;

//import org.apache.commons.dbcp.BasicDataSource;
//import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.BasicDataSourceFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ReproDBCP {
    Logger LOG = LogManager.getLogger(ReproDBCP.class);
    BasicDataSource ds;

    public static void main(String[] args) throws Exception {
        ReproDBCP repro = new ReproDBCP();
        try {
            repro.setup();
            repro.repro();
        } finally {
            repro.close();
        }
    }

    public void repro() throws InterruptedException {
        int numberOfThreads = 1;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        for (int i = 0; i < numberOfThreads; ++i) {
            executorService.submit(new ReproRunnable());
        }
        executorService.shutdown();
        executorService.awaitTermination(300, TimeUnit.SECONDS);
    }

    public void setup() throws Exception {
        String driverName = org.postgresql.Driver.class.getCanonicalName();
        String jdbcUrl = "jdbc:postgresql://127.0.0.1:5433/repro";

        Properties props = new Properties();
        props.put("driverClassName", driverName);
        props.put("username", "repro");
        props.put("password", "repro");
        props.put("url", jdbcUrl);
        props.put("maxActive", 1);
        props.put("maxTotal", 1);
        props.put("maxWait", 500);
        props.put("fastFailValidation", "false");
        props.put("testOnBorrow", "false");
        props.put("testOnReturn", "false");
        props.put("testWhileIdle", "false");
        props.put("validationQuery", "SELECT 1");
        props.put("timeBetweenEvictionRunsMillis", 10_000);
        props.put("numTestsPerEvictionRun", 10);
        props.put("transactionIsolation", "read-committed");

        ds = (BasicDataSource) BasicDataSourceFactory.createDataSource(props);
        //ds.start();
    }

    public void close() throws SQLException {
        if (ds != null) {
            ds.close();
        }
    }

    class ReproRunnable implements Runnable {

        @Override
        public void run() {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 300_000) {
                Connection conn = null;
                try {
                    conn = ds.getConnection();
                    LOG.info("Executing query...");
                    conn.createStatement().execute("INSERT INTO my_data VALUES(1, 'dbcp2', 'dbcp2')");
                    try {
                        Thread.sleep(new Random(500).nextInt(10000));
                    } catch (InterruptedException e) {
                        // no-op
                    }
                    LOG.info("Executed");
                } catch (Exception e1) {
                    LOG.warn(e1.getMessage(), e1);
                } finally {
                    if (conn != null) {
                        try {
                            conn.close();
                        } catch (SQLException e) {
                            LOG.warn(e);
                        }
                    }
                }
                try {
                    Thread.sleep(new Random(100).nextInt(200));
                } catch (InterruptedException e) {
                    // no-op
                }
            }
        }
    }
}
