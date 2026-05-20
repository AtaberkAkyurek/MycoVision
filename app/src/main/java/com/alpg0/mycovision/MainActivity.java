package com.alpg0.mycovision;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.alpg0.mycovision.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnCaptureUpload.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ImageInputActivity.class);
            startActivity(intent);
        });

        binding.btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });

        binding.btnSafety.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SafetyActivity.class);
            startActivity(intent);
        });
    }
}