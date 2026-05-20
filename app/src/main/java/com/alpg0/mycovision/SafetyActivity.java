package com.alpg0.mycovision;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.alpg0.mycovision.databinding.ActivitySafetyBinding;

public class SafetyActivity extends AppCompatActivity {

    private ActivitySafetyBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySafetyBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBackHome.setOnClickListener(v -> finish());
    }
}
