package com.alpg0.mycovision;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.alpg0.mycovision.databinding.ActivityLoginBinding;
import com.alpg0.mycovision.db.User;

public class LoginActivity extends AppCompatActivity {

    private ActivityLoginBinding binding;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);
        if (session.isLoggedIn()) {
            navigateAfterLogin();
            return;
        }

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnLogin.setOnClickListener(v -> doLogin());

        binding.btnGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void doLogin() {
        String username = binding.etUsername.getText() != null
                ? binding.etUsername.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null
                ? binding.etPassword.getText().toString() : "";

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please enter username and password.", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnLogin.setEnabled(false);
        new Thread(() -> {
            try {
                MysqlClient.UserRow row = MysqlClient.authenticate(username, password);

                runOnUiThread(() -> {
                    binding.btnLogin.setEnabled(true);
                    if (row == null) {
                        Toast.makeText(this, "Invalid username or password.", Toast.LENGTH_SHORT).show();
                    } else {
                        User u = new User();
                        u.id        = row.id;
                        u.username  = row.username;
                        u.role      = row.role;
                        session.saveLogin(u);
                        Toast.makeText(this, "Welcome, " + u.username + "!", Toast.LENGTH_SHORT).show();
                        navigateAfterLogin();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.btnLogin.setEnabled(true);
                    new AlertDialog.Builder(this)
                            .setTitle("Connection Error")
                            .setMessage("Could not reach MySQL server at "
                                    + MysqlClient.HOST + ":" + MysqlClient.PORT
                                    + "\n\n" + e.getMessage()
                                    + "\n\nCheck:\n• MySQL is running on PC\n• Firewall allows port 3306\n• MySQL bind-address = 0.0.0.0")
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }).start();
    }

    private void navigateAfterLogin() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
