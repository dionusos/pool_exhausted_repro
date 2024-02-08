package org.apache.dionusos.pool_exhausted_repro;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.openjpa.persistence.OpenJPAEntityManagerFactorySPI;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.Persistence;
import java.io.BufferedInputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ReproOneThread {
    private static final Logger LOG = LogManager.getLogger(ReproOneThread.class);
    EntityManagerFactory factory;

    public static void main(String[] args) throws InterruptedException {
        LOG.info("Initialising...");
        ReproOneThread repro = new ReproOneThread();
        repro.setup();
        LOG.info("Executing...");
        repro.repro();
    }

    public void repro() throws InterruptedException {
        new ReproRunnable().run();
        factory.close();
    }

    class ReproRunnable implements Runnable {

        @Override
        public void run() {
            while (true) {
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
                    System.out.println("Press Enter to continue ...");
                    new InputStreamReader(new BufferedInputStream(System.in)).read();
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
            }
        }
    }

    public void setup() {
        //String driverName = "org.postgresql.Driver";
        String driverName = TrackingDriver.class.getCanonicalName();
        String jdbcUrl = "jdbc:tracking://postgresql::127.0.0.1:5433/repro";
        String persistenceUnit = System.getProperty("persistenceUnit");

        StringBuilder connectionProperties = new StringBuilder();
        connectionProperties.append("DriverClassName=").append(driverName).append(",");
        connectionProperties.append("Url=").append(jdbcUrl).append(",");
        connectionProperties.append("MaxActive=").append(1).append(",");
        connectionProperties.append("MaxTotal=").append(2).append(",");
        //connectionProperties.append("MaxIdle=").append(2).append(",");
        connectionProperties.append("fastFailValidation=").append("false").append(",");
        connectionProperties.append("TestOnBorrow=").append("false").append(",");
        connectionProperties.append("TestOnReturn=").append("false").append(",");
        connectionProperties.append("TestWhileIdle=").append("false").append(",");
        //connectionProperties.append("ValidationQuery=").append("SELECT 1").append(",");
        connectionProperties.append("timeBetweenEvictionRunsMillis=").append(10_000).append(",");
        connectionProperties.append("numTestsPerEvictionRun=").append(10).append(",");

        final Properties props = new Properties();
        props.setProperty("openjpa.ConnectionProperties", connectionProperties.toString());
        props.setProperty("openjpa.ConnectionPassword", "repro");
        props.setProperty("openjpa.ConnectionUserName", "repro");
        props.setProperty("openjpa.ConnectionFactoryProperties", "PrintParameters=true");
        props.setProperty("openjpa.Log", "log4j");

        factory = Persistence.createEntityManagerFactory(persistenceUnit, props);
        EntityManager entityManager = factory.createEntityManager();

        entityManager.getTransaction().begin();
        OpenJPAEntityManagerFactorySPI spi = (OpenJPAEntityManagerFactorySPI) factory;
        LOG.info("JPA configuration: {}", spi.getConfiguration().getConnectionProperties());
        entityManager.getTransaction().commit();
        entityManager.close();
    }
}
