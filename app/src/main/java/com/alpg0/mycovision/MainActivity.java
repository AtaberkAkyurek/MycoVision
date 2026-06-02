package com.alpg0.mycovision;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.alpg0.mycovision.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);

        // If not logged in, redirect to login screen
        if (!session.isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.tvWelcome.setText("Hello, " + session.getUsername()
                + (session.isAdmin() ? " (Admin)" : ""));

        if (session.isAdmin()) {
            binding.btnAdmin.setVisibility(View.VISIBLE);
        }

        binding.btnCaptureUpload.setOnClickListener(v ->
                startActivity(new Intent(this, ImageInputActivity.class)));

        binding.btnHistory.setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));

        binding.btnSafety.setOnClickListener(v ->
                startActivity(new Intent(this, SafetyActivity.class)));

        binding.btnAdmin.setOnClickListener(v ->
                startActivity(new Intent(this, AdminDashboardActivity.class)));

        binding.btnLogout.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Logout")
                        .setMessage("Are you sure you want to log out?")
                        .setPositiveButton("Logout", (d, w) -> {
                            session.logout();
                            startActivity(new Intent(this, LoginActivity.class));
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());
    }
}
