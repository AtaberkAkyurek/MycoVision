package com.alpg0.mycovision;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.alpg0.mycovision.databinding.ActivityRegisterBinding;
import com.alpg0.mycovision.db.User;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRegister.setOnClickListener(v -> doRegister());
    }

    private void doRegister() {
        String username = binding.etUsername.getText() != null
                ? binding.etUsername.getText().toString().trim() : "";
        String password = binding.etPassword.getText() != null
                ? binding.etPassword.getText().toString() : "";
        String confirm  = binding.etConfirm.getText() != null
                ? binding.etConfirm.getText().toString() : "";

        if (TextUtils.isEmpty(username) || username.length() < 3) {
            Toast.makeText(this, "Username must be at least 3 characters.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirm)) {
            Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnRegister.setEnabled(false);
        new Thread(() -> {
            try {
                if (MysqlClient.userExists(username)) {
                    runOnUiThread(() -> {
                        binding.btnRegister.setEnabled(true);
                        Toast.makeText(this, "Username already taken.", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                long id = MysqlClient.registerUser(username, password, User.ROLE_USER);

                runOnUiThread(() -> {
                    binding.btnRegister.setEnabled(true);
                    if (id > 0) {
                        Toast.makeText(this, "Account created. You can now sign in.", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Registration failed.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    binding.btnRegister.setEnabled(true);
                    new AlertDialog.Builder(this)
                            .setTitle("Connection Error")
                            .setMessage("Could not reach MySQL server.\n\n" + e.getMessage())
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        }).start();
    }
}
