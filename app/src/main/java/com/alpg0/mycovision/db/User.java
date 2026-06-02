package com.alpg0.mycovision.db;

/**
 * Lightweight POJO carrying role constants and session payload.
 * Authentication itself lives in the remote MySQL backend (see MysqlClient).
 * This class is no longer a Room entity.
 */
public class User {

    public static final String ROLE_USER  = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    public long id;
    public String username;
    public String role;
}
