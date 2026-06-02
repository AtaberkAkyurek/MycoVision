package com.alpg0.mycovision;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL client for MycoVision backend.
 * Schema (database: mycovision_db):
 *   users(id, kullanici_adi, sifre, rol)
 *   scan (id, user_id, image, scan_datetime [, label, confidence])
 *
 * Connection is obtained via {@link DbConfig#getConnection()}.
 */
public class MysqlClient {

    public static final String HOST = DbConfig.HOST;
    public static final String PORT = DbConfig.PORT;

    // ── Models ─────────────────────────────────────────────────────────────
    public static class UserRow {
        public final long id;
        public final String username;
        public final String role;

        public UserRow(long id, String username, String role) {
            this.id = id;
            this.username = username;
            this.role = role;
        }
    }

    public static class ScanRow {
        public final long id;
        public final long userId;
        public final String image;
        public final long timestamp;
        public final String label;
        public final float confidence;

        public ScanRow(long id, long userId, String image, long timestamp,
                       String label, float confidence) {
            this.id = id;
            this.userId = userId;
            this.image = image;
            this.timestamp = timestamp;
            this.label = label;
            this.confidence = confidence;
        }
    }

    // ── Auth ───────────────────────────────────────────────────────────────

    /** Returns a UserRow if credentials are valid, null otherwise. */
    public static UserRow authenticate(String username, String password) throws Exception {
        String sql = "SELECT id, kullanici_adi, rol FROM users " +
                "WHERE kullanici_adi = ? AND sifre = ? LIMIT 1";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new UserRow(rs.getLong("id"),
                            rs.getString("kullanici_adi"),
                            rs.getString("rol"));
                }
                return null;
            }
        }
    }

    /** Returns true if username already exists. */
    public static boolean userExists(String username) throws Exception {
        String sql = "SELECT 1 FROM users WHERE kullanici_adi = ? LIMIT 1";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Inserts a new user. Returns generated id, or -1 on failure. */
    public static long registerUser(String username, String password, String role) throws Exception {
        String sql = "INSERT INTO users (kullanici_adi, sifre, rol) VALUES (?, ?, ?)";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.setString(3, role);
            int rows = stmt.executeUpdate();
            if (rows == 0) return -1;
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            return -1;
        }
    }

    // ── Scans ──────────────────────────────────────────────────────────────

    /** Inserts a scan row. Tries the extended schema (with label/confidence)
     *  first, falls back to the minimal schema if those columns don't exist. */
    public static long insertScan(long userId, String imageUri, String label, float confidence)
            throws Exception {
        String extendedSql =
                "INSERT INTO scan (user_id, image, scan_datetime, label, confidence) " +
                "VALUES (?, ?, NOW(), ?, ?)";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(extendedSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, userId);
            stmt.setString(2, imageUri);
            stmt.setString(3, label);
            stmt.setFloat(4, confidence);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            return -1;
        } catch (SQLException ex) {
            // Fall back to minimal schema (id, user_id, image, scan_datetime)
            String minSql = "INSERT INTO scan (user_id, image, scan_datetime) VALUES (?, ?, NOW())";
            try (Connection conn = DbConfig.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(minSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, userId);
                stmt.setString(2, imageUri);
                stmt.executeUpdate();
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) return keys.getLong(1);
                }
                return -1;
            }
        }
    }

    /** Reads scans for one user, newest first. Handles missing label/confidence columns. */
    public static List<ScanRow> getScansByUser(long userId) throws Exception {
        return readScans(
                "SELECT * FROM scan WHERE user_id = ? ORDER BY scan_datetime DESC",
                userId);
    }

    /** Reads ALL scans (admin only), newest first. */
    public static List<ScanRow> getAllScans() throws Exception {
        return readScans("SELECT * FROM scan ORDER BY scan_datetime DESC", null);
    }

    private static List<ScanRow> readScans(String sql, Long userIdParam) throws Exception {
        List<ScanRow> list = new ArrayList<>();
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (userIdParam != null) stmt.setLong(1, userIdParam);
            try (ResultSet rs = stmt.executeQuery()) {
                // Detect optional columns once
                boolean hasLabel = hasColumn(rs, "label");
                boolean hasConf  = hasColumn(rs, "confidence");
                while (rs.next()) {
                    long id   = rs.getLong("id");
                    long uid  = rs.getLong("user_id");
                    String img = rs.getString("image");
                    java.sql.Timestamp ts = rs.getTimestamp("scan_datetime");
                    long timestamp = (ts != null) ? ts.getTime() : 0L;
                    String label = hasLabel ? rs.getString("label") : "UNKNOWN";
                    float conf   = hasConf  ? rs.getFloat("confidence") : 0f;
                    list.add(new ScanRow(id, uid, img, timestamp, label, conf));
                }
            }
        }
        return list;
    }

    private static boolean hasColumn(ResultSet rs, String col) {
        try {
            rs.findColumn(col);
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static int getScanCount() throws Exception {
        String sql = "SELECT COUNT(*) FROM scan";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public static int getScanCountByLabel(String label) throws Exception {
        // If label column doesn't exist, treat as 0
        String sql = "SELECT COUNT(*) FROM scan WHERE label = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, label);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException ignored) {
            return 0;
        }
    }

    public static float getAverageConfidence() throws Exception {
        String sql = "SELECT AVG(confidence) FROM scan";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getFloat(1) : 0f;
        } catch (SQLException ignored) {
            return 0f;
        }
    }

    public static void deleteScanById(long id) throws Exception {
        String sql = "DELETE FROM scan WHERE id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    public static void deleteScansByUser(long userId) throws Exception {
        String sql = "DELETE FROM scan WHERE user_id = ?";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, userId);
            stmt.executeUpdate();
        }
    }

    public static void deleteAllScans() throws Exception {
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM scan")) {
            stmt.executeUpdate();
        }
    }

    // ── Admin dashboard queries ────────────────────────────────────────────

    public static int getUserCount() throws Exception {
        String sql = "SELECT COUNT(*) FROM users";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public static List<UserRow> getAllUsers() throws Exception {
        List<UserRow> list = new ArrayList<>();
        String sql = "SELECT id, kullanici_adi, rol FROM users ORDER BY id";
        try (Connection conn = DbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new UserRow(rs.getLong("id"),
                        rs.getString("kullanici_adi"),
                        rs.getString("rol")));
            }
        }
        return list;
    }

    /** Quick connectivity test. */
    public static boolean pingServer() {
        try (Connection conn = DbConfig.getConnection()) {
            return conn.isValid(3);
        } catch (Exception e) {
            return false;
        }
    }
}
