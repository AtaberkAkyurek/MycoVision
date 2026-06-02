package com.alpg0.mycovision;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alpg0.mycovision.databinding.ActivityHistoryBinding;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding binding;
    private HistoryAdapter adapter;

    private final ActivityResultLauncher<String> writePermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) exportToCsv();
                else Toast.makeText(this, "Storage permission denied.", Toast.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new HistoryAdapter(new ArrayList<>());
        binding.recyclerHistory.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerHistory.setAdapter(adapter);

        binding.btnBackHome.setOnClickListener(v -> finish());

        binding.btnClearAll.setOnClickListener(v -> {
            if (adapter.getItemCount() == 0) return;
            new AlertDialog.Builder(this)
                    .setTitle("Clear History")
                    .setMessage("Delete all " + adapter.getItemCount() + " scan records?")
                    .setPositiveButton("Clear All", (d, w) ->
                            new Thread(() -> {
                                try {
                                    SessionManager s = new SessionManager(this);
                                    if (s.isAdmin()) {
                                        MysqlClient.deleteAllScans();
                                    } else {
                                        MysqlClient.deleteScansByUser(s.getUserId());
                                    }
                                } catch (Exception e) {
                                    runOnUiThread(() -> Toast.makeText(this,
                                            "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                }
                                runOnUiThread(this::loadHistory);
                            }).start()
                    )
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        binding.btnExportCsv.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
                    writePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    return;
                }
            }
            exportToCsv();
        });

        loadHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHistory();
    }

    private void loadHistory() {
        new Thread(() -> {
            SessionManager session = new SessionManager(this);
            List<MysqlClient.ScanRow> records = new ArrayList<>();
            String error = null;
            try {
                records = session.isAdmin()
                        ? MysqlClient.getAllScans()
                        : MysqlClient.getScansByUser(session.getUserId());
            } catch (Exception e) {
                error = e.getMessage();
            }

            final List<MysqlClient.ScanRow> finalRecords = records;
            final String finalError = error;
            runOnUiThread(() -> {
                adapter.setData(finalRecords);
                boolean empty = finalRecords.isEmpty();
                binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.recyclerHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
                binding.btnClearAll.setVisibility(empty ? View.GONE : View.VISIBLE);
                boolean showCsv = !empty && session.isAdmin();
                binding.btnExportCsv.setVisibility(showCsv ? View.VISIBLE : View.GONE);

                if (finalError != null) {
                    Toast.makeText(this, "MySQL: " + finalError, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    private void exportToCsv() {
        new Thread(() -> {
            List<MysqlClient.ScanRow> records;
            try {
                SessionManager session = new SessionManager(this);
                records = session.isAdmin()
                        ? MysqlClient.getAllScans()
                        : MysqlClient.getScansByUser(session.getUserId());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Could not fetch records: " + e.getMessage(), Toast.LENGTH_LONG).show());
                return;
            }

            if (records.isEmpty()) {
                runOnUiThread(() -> Toast.makeText(this, "No records to export.", Toast.LENGTH_SHORT).show());
                return;
            }

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d yyyy HH:mm", Locale.getDefault());
                StringBuilder csv = new StringBuilder("ID,UserID,Date,Label,Confidence(%)\n");
                for (MysqlClient.ScanRow r : records) {
                    csv.append(r.id).append(",")
                            .append(r.userId).append(",")
                            .append(sdf.format(new Date(r.timestamp))).append(",")
                            .append(r.label).append(",")
                            .append((int)(r.confidence * 100)).append("\n");
                }
                byte[] csvBytes = csv.toString().getBytes();
                final String fileName = "mycovision_history.csv";
                String tempSavedPath = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                        values.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
                        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                        if (uri != null) {
                            OutputStream os = getContentResolver().openOutputStream(uri);
                            if (os != null) {
                                os.write(csvBytes);
                                os.flush();
                                os.close();
                                tempSavedPath = "Downloads/" + fileName;
                            }
                        }
                    } catch (Exception ignored) { }
                }

                if (tempSavedPath == null) {
                    File fallbackDir = getExternalFilesDir(null);
                    if (fallbackDir == null) fallbackDir = getFilesDir();
                    File outFile = new File(fallbackDir, fileName);
                    try (FileWriter writer = new FileWriter(outFile)) {
                        writer.write(csv.toString());
                    }
                    tempSavedPath = outFile.getAbsolutePath();
                }

                final String savedPath = tempSavedPath;
                final int count = records.size();
                runOnUiThread(() ->
                        new AlertDialog.Builder(this)
                                .setTitle("Export Successful")
                                .setMessage(count + " records saved to:\n\n" + savedPath)
                                .setPositiveButton("OK", null)
                                .show()
                );
            } catch (IOException e) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ── RecyclerView Adapter ────────────────────────────────────────────────

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        private List<MysqlClient.ScanRow> data;
        private final SimpleDateFormat fmt =
                new SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault());

        HistoryAdapter(List<MysqlClient.ScanRow> data) {
            this.data = data;
        }

        void setData(List<MysqlClient.ScanRow> newData) {
            this.data = newData;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() { return data.size(); }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_scan_history, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            MysqlClient.ScanRow r = data.get(position);
            boolean isPoisonous = "POISONOUS".equals(r.label);

            holder.tvLabel.setText(r.label != null ? r.label : "UNKNOWN");
            holder.tvLabel.setTextColor(ContextCompat.getColor(
                    holder.itemView.getContext(),
                    isPoisonous ? R.color.poisonous_red : R.color.edible_green));

            holder.tvDate.setText(fmt.format(new Date(r.timestamp)));
            holder.tvConf.setText("Confidence: " + (int)(r.confidence * 100) + "%");

            if (r.image != null && !r.image.isEmpty()) {
                try {
                    holder.ivThumb.setImageURI(Uri.parse(r.image));
                } catch (Exception e) {
                    holder.ivThumb.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else {
                holder.ivThumb.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            holder.btnDelete.setOnClickListener(v -> {
                long id = r.id;
                new AlertDialog.Builder(holder.itemView.getContext())
                        .setMessage("Delete this scan record?")
                        .setPositiveButton("Delete", (d, w) ->
                                new Thread(() -> {
                                    try {
                                        MysqlClient.deleteScanById(id);
                                    } catch (Exception e) {
                                        runOnUiThread(() -> Toast.makeText(HistoryActivity.this,
                                                "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                                    }
                                    runOnUiThread(HistoryActivity.this::loadHistory);
                                }).start()
                        )
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvLabel, tvDate, tvConf;
            ImageView ivThumb, btnDelete;

            ViewHolder(View v) {
                super(v);
                tvLabel   = v.findViewById(R.id.tvItemLabel);
                tvDate    = v.findViewById(R.id.tvItemDate);
                tvConf    = v.findViewById(R.id.tvItemConf);
                ivThumb   = v.findViewById(R.id.ivItemThumb);
                btnDelete = v.findViewById(R.id.btnItemDelete);
            }
        }
    }
}
