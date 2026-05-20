package com.alpg0.mycovision;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.alpg0.mycovision.databinding.ActivityResultBinding;
import com.alpg0.mycovision.db.AppDatabase;
import com.alpg0.mycovision.db.ScanRecord;

public class ResultActivity extends AppCompatActivity {

    private ActivityResultBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityResultBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        String label      = getIntent().getStringExtra("label");
        String confidence = getIntent().getStringExtra("confidence");
        float  confFloat  = getIntent().getFloatExtra("confidence_float", 0f);
        String warning    = getIntent().getStringExtra("warning");
        String imageUri   = getIntent().getStringExtra("image_uri");

        if (label      == null) label      = "UNKNOWN";
        if (confidence == null) confidence = "0%";
        if (warning    == null) warning    = "Always consult an expert before consuming wild mushrooms.";

        boolean isPoisonous = "POISONOUS".equals(label);

        // ── Result label ────────────────────────────────────────────────────
        binding.tvLabel.setText(isPoisonous ? "POISONOUS" : "EDIBLE");
        int labelColor = ContextCompat.getColor(this,
                isPoisonous ? R.color.poisonous_red : R.color.edible_green);
        binding.tvLabel.setTextColor(labelColor);

        // ── Result card background ──────────────────────────────────────────
        binding.cardResult.setCardBackgroundColor(
                ContextCompat.getColor(this,
                        isPoisonous ? R.color.poisonous_bg : R.color.edible_bg));

        // ── Confidence ──────────────────────────────────────────────────────
        binding.tvConfidence.setText("Confidence: " + confidence);
        binding.progressConfidence.setProgress((int) (confFloat * 100));

        // ── Warning ─────────────────────────────────────────────────────────
        binding.tvWarning.setText(warning);

        // ── Preview image ───────────────────────────────────────────────────
        if (imageUri != null && !imageUri.isEmpty()) {
            try {
                binding.ivResultImage.setImageURI(Uri.parse(imageUri));
            } catch (Exception e) {
                // ignore if URI is invalid
            }
        }

        // ── Save to history on background thread ────────────────────────────
        if (imageUri != null && !imageUri.isEmpty()) {
            final String finalLabel = label;
            final float  finalConf  = confFloat;
            final String finalUri   = imageUri;
            new Thread(() -> {
                ScanRecord record = new ScanRecord(
                        System.currentTimeMillis(), finalUri, finalLabel, finalConf);
                AppDatabase.getInstance(this).scanDao().insertScan(record);
            }).start();
        }

        // ── Navigation ──────────────────────────────────────────────────────
        binding.btnBackHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        });

        binding.btnNewScan.setOnClickListener(v -> finish());

        binding.btnSafety.setOnClickListener(v ->
                startActivity(new Intent(this, SafetyActivity.class)));
    }
}
