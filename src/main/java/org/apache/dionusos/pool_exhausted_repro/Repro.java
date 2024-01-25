package org.apache.dionusos.pool_exhausted_repro;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.openjpa.persistence.OpenJPAEntityManagerFactorySPI;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Repro {
    private static final Logger LOG = LogManager.getLogger(Repro.class);
    EntityManagerFactory factory;

    public static void main(String[] args) {
        LOG.info("Initialising...");
        Repro repro = new Repro();
        repro.setup();
        LOG.info("Executing...");
        repro.repro();
    }

    public void repro() {
        int numberOfThreads = 4;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        for (int i = 0; i < numberOfThreads; ++i) {
            executorService.submit(new ReproRunnable());
        }
        executorService.shutdown();
    }

    class ReproRunnable implements Runnable {

        @Override
        public void run() {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 300_000) {
                EntityManager em = factory.createEntityManager();
                EntityTransaction transaction = em.getTransaction();

                try {
                    if (!transaction.isActive()) {
                        transaction.begin();
                    }
                    MyBean bean = new MyBean();
                    bean.setId(1);
                    bean.setKey("Mr");
                    bean.setValue("Bean");
                    LOG.info("Persisting bean...");
                    em.persist(bean);
                    if (transaction.isActive()) {
                        transaction.commit();
                    }
                } catch (Exception e1) {
                    LOG.warn(e1.getMessage(), e1);
                    if (em.getTransaction().isActive()) {
                        try {
                            em.getTransaction().rollback();
                        } catch (Exception e2) {
                            LOG.warn(e2.getMessage(), e2);
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

    public void setup() {
        String driverName = "org.postgresql.Driver";
        String jdbcUrl = "jdbc:postgresql://127.0.0.1:5433/repro";
        String persistenceUnit = System.getProperty("persistenceUnit");

        StringBuilder connectionProperties = new StringBuilder();
        connectionProperties.append("DriverClassName=").append(driverName).append(",");
        connectionProperties.append("Url=").append(jdbcUrl).append(",");
        connectionProperties.append("MaxActive=").append(3).append(",");
        //connectionProperties.append("MaxIdle=").append(2).append(",");
        connectionProperties.append("TestOnBorrow=").append("true").append(",");
        connectionProperties.append("TestOnReturn=").append("true").append(",");
        connectionProperties.append("TestWhileIdle=").append("true").append(",");
        connectionProperties.append("ValidationQuery=").append("SELECT 1").append(",");
        connectionProperties.append("timeBetweenEvictionRunsMillis=").append(10_000).append(",");
        connectionProperties.append("numTestsPerEvictionRun=").append(10).append(",");

        final Properties props = new Properties();
        props.setProperty("openjpa.ConnectionProperties", connectionProperties.toString());
        props.setProperty("openjpa.ConnectionPassword", "repro");
        props.setProperty("openjpa.ConnectionUserName", "repro");
        props.setProperty("openjpa.ConnectionFactoryProperties", "PrintParameters=true");
        props.setProperty("openjpa.Log", "slf4j");

        factory = Persistence.createEntityManagerFactory(persistenceUnit, props);
        EntityManager entityManager = factory.createEntityManager();

        entityManager.getTransaction().begin();
        OpenJPAEntityManagerFactorySPI spi = (OpenJPAEntityManagerFactorySPI) factory;
        LOG.info("JPA configuration: {}", spi.getConfiguration().getConnectionProperties());
        entityManager.getTransaction().commit();
        entityManager.close();
    }
}
