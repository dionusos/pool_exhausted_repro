package org.apache.dionusos.pool_exhausted_repro;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ReproSqlDriver {

    public static void main(String[] args) throws SQLException, IOException {

        try (Connection connection = createConnection("postgresql")) {
            System.out.println("Executing INSERT ...");
            connection.createStatement().execute("INSERT INTO MY_DATA VALUES(1, 'foo', 'bar')");
            System.out.println("Press Enter to continue ...");
            new InputStreamReader(new BufferedInputStream(System.in)).read();
            System.out.println("Executing INSERT again ...");
            try {
                connection.createStatement().execute("INSERT INTO MY_DATA VALUES(1, 'foo', 'bar')");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                System.out.println("Connection is " + (connection.isClosed() ? "closed." : "not closed."));
            }
        }
    }

    public static Connection createConnection(String dbType) throws SQLException {
        Connection connection = null;
        switch (dbType) {
            case "mysql":
                // kill MySQL connection
                // /usr/bin/mysql -urepro -prepro -e "select ID from performance_schema.processlist WHERE COMMAND = 'Sleep';" | xargs -I '{}' /usr/bin/mysql -urepro -prepro -e "KILL {}"
                connection = DriverManager.getConnection(
                        "jdbc:mysql://localhost:3307/repro",
                        "repro",
                        "repro");
                break;
            case "postgresql":
                // kill PostreSQL connection
                // /usr/bin/psql -Urepro -c "SELECT pg_terminate_backend(pg_stat_activity.pid) FROM pg_stat_activity where application_name = 'PostgreSQL JDBC Driver' ;"
                connection = DriverManager.getConnection(
                        "jdbc:postgresql://127.0.0.1:5433/repro",
                        "repro",
                        "repro");
                break;
            case "oracle":
                // CREATE TABLE REPRO.MY_DATA (
                //	ID INTEGER NULL,
                //	COLUMN_KEY VARCHAR2(100) NULL,
                //	VALUE VARCHAR2(100) NULL
                //);
                // kill Oracle connection system/<system-password>
                // SELECT SID, SERIAL# FROM GV$SESSION WHERE USERNAME = 'REPRO' AND OSUSER = 'LOCAL_MACHINE_USER_NAME';
                // ALTER SYSTEM KILL SESSION '2666, 30406';
                connection = DriverManager.getConnection(
                        "jdbc:oracle:thin:@//oracle_host:1521/orcl12c",
                        "repro",
                        "repro");
                break;
        }

        return connection;
    }
}
