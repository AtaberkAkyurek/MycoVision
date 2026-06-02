package com.alpg0.mycovision;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alpg0.mycovision.databinding.ActivityAdminDashboardBinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminDashboardActivity extends AppCompatActivity {

    private static final String ROLE_ADMIN = "ADMIN";

    private ActivityAdminDashboardBinding binding;
    private UsersAdapter usersAdapter;
    private ScansAdapter scansAdapter;
    private final SimpleDateFormat fmt =
            new SimpleDateFormat("MMM d, yyyy  HH:mm", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminDashboardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        usersAdapter = new UsersAdapter(new ArrayList<>());
        scansAdapter = new ScansAdapter(new ArrayList<>());

        binding.recyclerUsers.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerUsers.setAdapter(usersAdapter);

        binding.recyclerAllScans.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerAllScans.setAdapter(scansAdapter);

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRefresh.setOnClickListener(v -> loadData());

        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        new Thread(() -> {
            int totalScans = 0;
            int edible = 0;
            int poisonous = 0;
            int avgConf = 0;
            int totalUsers = 0;
            List<MysqlClient.UserRow> users = new ArrayList<>();
            List<MysqlClient.ScanRow> scans = new ArrayList<>();
            String error = null;

            try {
                totalScans = MysqlClient.getScanCount();
                edible     = MysqlClient.getScanCountByLabel("EDIBLE");
                poisonous  = MysqlClient.getScanCountByLabel("POISONOUS");
                float avg  = MysqlClient.getAverageConfidence();
                avgConf    = Math.round(avg * 100);
                totalUsers = MysqlClient.getUserCount();
                users      = MysqlClient.getAllUsers();
                scans      = MysqlClient.getAllScans();
            } catch (Exception e) {
                error = e.getMessage();
            }

            final int finalTotalScans = totalScans;
            final int finalEdible = edible;
            final int finalPoisonous = poisonous;
            final int finalAvgConf = avgConf;
            final int finalTotalUsers = totalUsers;
            final List<MysqlClient.UserRow> finalUsers = users;
            final List<MysqlClient.ScanRow> finalScans = scans;
            final String finalError = error;

            runOnUiThread(() -> {
                binding.tvTotalScans.setText(String.valueOf(finalTotalScans));
                binding.tvTotalUsers.setText(String.valueOf(finalTotalUsers));
                binding.tvEdibleCount.setText(String.valueOf(finalEdible));
                binding.tvPoisonousCount.setText(String.valueOf(finalPoisonous));
                binding.tvAvgConfidence.setText(finalAvgConf + "%");

                usersAdapter.setData(finalUsers);
                scansAdapter.setData(finalScans);

                if (finalError != null) {
                    Toast.makeText(this, "MySQL: " + finalError, Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    // ── Users adapter ──────────────────────────────────────────────────────
    class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.UserVH> {
        private List<MysqlClient.UserRow> data;

        UsersAdapter(List<MysqlClient.UserRow> data) { this.data = data; }

        void setData(List<MysqlClient.UserRow> newData) {
            this.data = newData;
            notifyDataSetChanged();
        }

        @Override
        public UserVH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_user, parent, false);
            return new UserVH(v);
        }

        @Override
        public void onBindViewHolder(UserVH h, int pos) {
            MysqlClient.UserRow u = data.get(pos);
            h.tvUsername.setText(u.username);
            h.tvCreatedAt.setText("ID: " + u.id);
            h.tvRoleBadge.setText(u.role != null ? u.role : "USER");
            int bg = ROLE_ADMIN.equals(u.role)
                    ? ContextCompat.getColor(AdminDashboardActivity.this, R.color.poisonous_red)
                    : ContextCompat.getColor(AdminDashboardActivity.this, R.color.myco_green);
            h.tvRoleBadge.setBackgroundColor(bg);
        }

        @Override
        public int getItemCount() { return data.size(); }

        class UserVH extends RecyclerView.ViewHolder {
            TextView tvUsername, tvCreatedAt, tvRoleBadge;
            UserVH(View v) {
                super(v);
                tvUsername  = v.findViewById(R.id.tvUsername);
                tvCreatedAt = v.findViewById(R.id.tvCreatedAt);
                tvRoleBadge = v.findViewById(R.id.tvRoleBadge);
            }
        }
    }

    // ── Scans adapter ──────────────────────────────────────────────────────
    class ScansAdapter extends RecyclerView.Adapter<ScansAdapter.ScanVH> {
        private List<MysqlClient.ScanRow> data;

        ScansAdapter(List<MysqlClient.ScanRow> data) { this.data = data; }

        void setData(List<MysqlClient.ScanRow> newData) {
            this.data = newData;
            notifyDataSetChanged();
        }

        @Override
        public ScanVH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_scan_history, parent, false);
            return new ScanVH(v);
        }

        @Override
        public void onBindViewHolder(ScanVH h, int pos) {
            MysqlClient.ScanRow r = data.get(pos);
            boolean poisonous = "POISONOUS".equals(r.label);
            h.tvLabel.setText(r.label != null ? r.label : "UNKNOWN");
            h.tvLabel.setTextColor(ContextCompat.getColor(
                    AdminDashboardActivity.this,
                    poisonous ? R.color.poisonous_red : R.color.edible_green));
            h.tvDate.setText(fmt.format(new Date(r.timestamp)));
            h.tvConf.setText("User #" + r.userId + " · " + (int)(r.confidence * 100) + "%");

            if (r.image != null && !r.image.isEmpty()) {
                try {
                    h.ivThumb.setImageURI(Uri.parse(r.image));
                } catch (Exception e) {
                    h.ivThumb.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else {
                h.ivThumb.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            h.btnDelete.setVisibility(View.GONE);
        }

        @Override
        public int getItemCount() { return data.size(); }

        class ScanVH extends RecyclerView.ViewHolder {
            TextView tvLabel, tvDate, tvConf;
            ImageView ivThumb, btnDelete;
            ScanVH(View v) {
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
