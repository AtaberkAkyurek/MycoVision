package com.alpg0.mycovision;

import java.sql.Connection;
import java.sql.DriverManager;

public class DbConfig {

    public static final String HOST = "192.168.1.130";
    public static final String PORT = "3306";
    public static final String DB_NAME = "mycovision_db";

    public static final String USER = "root";
    public static final String PASSWORD = "admin123";

    public static final String URL =
            "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB_NAME
                    + "?useSSL=false"
                    + "&useUnicode=true"
                    + "&characterEncoding=UTF-8"
                    + "&autoReconnect=true";

    public static Connection getConnection() throws Exception {
        // Legacy MySQL Connector/J 5.1.x — driver class is com.mysql.jdbc.Driver
        // (8.x uses com.mysql.cj.jdbc.Driver but crashes on Android)
        Class.forName("com.mysql.jdbc.Driver");

        return DriverManager.getConnection(
                DbConfig.URL,
                DbConfig.USER,
                DbConfig.PASSWORD
        );
    }
}
