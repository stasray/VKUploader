package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PostgresManager {

    private static final String CREATE_TABLES = "CREATE TABLE IF NOT EXISTS public.metadata (\n" +
            "    id SERIAL PRIMARY KEY,\n" +
            "    name VARCHAR(255),\n" +
            "    size_bytes BIGINT,\n" +
            "    duration_s NUMERIC,\n" +
            "    codec CHAR(16),\n" +
            "    resolution CHAR(16),\n" +
            "    bitrate_kb_s INTEGER,\n" +
            "    profile CHAR(16)\n" +
            ");";

    private final String url;
    private final String user;
    private final String password;
    private Connection connection = null;

    public PostgresManager(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        createTable();
    }

    public void createTable() {
        try {
            if (connection == null) {
                this.connection = DriverManager.getConnection(url, user, password);
            }
            PreparedStatement pstmt = this.connection.prepareStatement(CREATE_TABLES);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public void add(boolean isAsync, String name, long sizeBytes, double duration, String codec, String resolution, int bitrate, String profile) {
        if (connection == null) {
            throw new RuntimeException("Connection is closed!");
        }
        Runnable task = () -> {
            String sql = "INSERT INTO public.metadata (name, size_bytes, duration_s, codec, resolution, bitrate_kb_s, profile) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try {
                PreparedStatement pstmt = connection.prepareStatement(sql);
                pstmt.setString(1, name);
                pstmt.setLong(2, sizeBytes);
                pstmt.setDouble(3, duration);
                pstmt.setString(4, codec);
                pstmt.setString(5, resolution);
                pstmt.setInt(6, bitrate);
                pstmt.setString(7, profile);
                int rowsAffected = pstmt.executeUpdate();
                System.out.println(rowsAffected + " row(s) inserted.");
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        };

        if (isAsync) {
            executor.execute(task);
        } else {
            task.run();
        }
    }

}
