package com.alpg0.mycovision;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alpg0.mycovision.databinding.ActivityHistoryBinding;
import com.alpg0.mycovision.db.AppDatabase;
import com.alpg0.mycovision.db.ScanRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {

    private ActivityHistoryBinding binding;
    private HistoryAdapter adapter;

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
                                AppDatabase.getInstance(this).scanDao().deleteAll();
                                runOnUiThread(this::loadHistory);
                            }).start()
                    )
                    .setNegativeButton("Cancel", null)
                    .show();
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
            List<ScanRecord> records = AppDatabase.getInstance(this).scanDao().getAllScans();
            runOnUiThread(() -> {
                adapter.setData(records);
                boolean empty = records.isEmpty();
                binding.tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.recyclerHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
                binding.btnClearAll.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        }).start();
    }

    // ── RecyclerView Adapter ────────────────────────────────────────────────

    class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        private List<ScanRecord> data;
        private final SimpleDateFormat fmt =
                new SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault());

        HistoryAdapter(List<ScanRecord> data) {
            this.data = data;
        }

        void setData(List<ScanRecord> newData) {
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
            ScanRecord r = data.get(position);
            boolean isPoisonous = "POISONOUS".equals(r.label);

            holder.tvLabel.setText(isPoisonous ? "POISONOUS" : "EDIBLE");
            holder.tvLabel.setTextColor(ContextCompat.getColor(
                    holder.itemView.getContext(),
                    isPoisonous ? R.color.poisonous_red : R.color.edible_green));

            holder.tvDate.setText(fmt.format(new Date(r.timestamp)));
            holder.tvConf.setText("Confidence: " + (int)(r.confidence * 100) + "%");

            // Load thumbnail via URI (no external library needed)
            if (r.imageUri != null && !r.imageUri.isEmpty()) {
                try {
                    holder.ivThumb.setImageURI(Uri.parse(r.imageUri));
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
                                    AppDatabase.getInstance(HistoryActivity.this)
                                            .scanDao().deleteScanById(id);
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
