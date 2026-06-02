package com.alpg0.mycovision;

import android.content.Context;
import android.content.SharedPreferences;

import com.alpg0.mycovision.db.User;

public class SessionManager {

    private static final String PREFS_NAME = "mycovision_session";
    private static final String KEY_USER_ID  = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_ROLE     = "role";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveLogin(User user) {
        prefs.edit()
                .putLong(KEY_USER_ID, user.id)
                .putString(KEY_USERNAME, user.username)
                .putString(KEY_ROLE, user.role)
                .apply();
    }

    public void logout() {
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return prefs.contains(KEY_USER_ID);
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1L);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public String getRole() {
        return prefs.getString(KEY_ROLE, User.ROLE_USER);
    }

    public boolean isAdmin() {
        return User.ROLE_ADMIN.equals(getRole());
    }
}
