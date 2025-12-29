package com.example.lostfound;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class MyReportsActivity extends AppCompatActivity {

    private TcpClient tcpClient;
    private LinearLayout reportsContainer;
    private LinearLayout emptyState;
    private ProgressBar loadingIndicator;

    private Chip chipAll, chipActive, chipResolved;

    private final List<ReportItem> allReports = new ArrayList<>();
    private String currentFilter = "ALL"; // ALL, ACTIVE, RESOLVED

    private int activeCount = 0;
    private int resolvedCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!UserIdentity.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_my_reports);

        reportsContainer = findViewById(R.id.reportsContainer);
        emptyState = findViewById(R.id.emptyState);
        loadingIndicator = findViewById(R.id.loadingIndicator);

        chipAll = findViewById(R.id.chipAll);
        chipActive = findViewById(R.id.chipActive);
        chipResolved = findViewById(R.id.chipResolved);

        setupFilterChips();
        setupBottomNavigation();

        // Connect to server
        tcpClient = new TcpClient(ServerConfig.SERVER_IP, ServerConfig.SERVER_PORT, msg -> runOnUiThread(() -> onServerMessage(msg)));
        tcpClient.connect();

        // Show loading and request reports
        showLoading(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh reports when returning to this screen
        if (tcpClient != null) {
            allReports.clear();
            activeCount = 0;
            resolvedCount = 0;
            showLoading(true);
            String username = UserIdentity.getID(this);
            tcpClient.send("REPORTS::GET_MINE::" + username);
        }
    }

    private void setupFilterChips() {
        chipAll.setOnClickListener(v -> {
            currentFilter = "ALL";
            updateChipStyles();
            displayFilteredReports();
        });

        chipActive.setOnClickListener(v -> {
            currentFilter = "ACTIVE";
            updateChipStyles();
            displayFilteredReports();
        });

        chipResolved.setOnClickListener(v -> {
            currentFilter = "RESOLVED";
            updateChipStyles();
            displayFilteredReports();
        });
    }

    private void updateChipStyles() {
        // Reset all chips to default style
        chipAll.setChipBackgroundColorResource(R.color.surface_white);
        chipAll.setTextColor(getColor(R.color.text_main));

        chipActive.setChipBackgroundColorResource(R.color.surface_white);
        chipActive.setTextColor(getColor(R.color.text_main));

        chipResolved.setChipBackgroundColorResource(R.color.surface_white);
        chipResolved.setTextColor(getColor(R.color.text_main));

        // Highlight selected chip
        Chip selectedChip;
        switch (currentFilter) {
            case "ACTIVE":
                selectedChip = chipActive;
                break;
            case "RESOLVED":
                selectedChip = chipResolved;
                break;
            default:
                selectedChip = chipAll;
        }
        selectedChip.setChipBackgroundColorResource(R.color.primary);
        selectedChip.setTextColor(getColor(R.color.surface_white));
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_my_reports);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_my_reports) return true;
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            if (id == R.id.nav_chat) {
                startActivity(new Intent(this, ChatListActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return true;
        });
    }

    private void onServerMessage(String raw) {
        String msg = raw == null ? "" : raw.trim();
        android.util.Log.d("MY_REPORTS", "From server: " + msg);

        if (msg.equals("MY_REPORTS_DONE")) {
            showLoading(false);
            updateChipCounts();
            displayFilteredReports();
            return;
        }

        if (msg.startsWith("MY_REPORT::")) {
            ReportItem report = ReportItem.fromServerString(msg);
            if (report != null) {
                allReports.add(report);
                if (report.isActive()) {
                    activeCount++;
                } else if (report.isResolved()) {
                    resolvedCount++;
                }
            }
            return;
        }

        if (msg.equals("RESOLVE_OK")) {
            Toast.makeText(this, "Report marked as resolved!", Toast.LENGTH_SHORT).show();
            refreshReports();
            return;
        }

        if (msg.startsWith("RESOLVE_FAIL")) {
            Toast.makeText(this, "Failed to resolve report", Toast.LENGTH_SHORT).show();
            return;
        }

        if (msg.equals("DELETE_OK")) {
            Toast.makeText(this, "Report deleted!", Toast.LENGTH_SHORT).show();
            refreshReports();
            return;
        }

        if (msg.startsWith("DELETE_FAIL")) {
            Toast.makeText(this, "Failed to delete report", Toast.LENGTH_SHORT).show();
            return;
        }
    }

    private void refreshReports() {
        allReports.clear();
        activeCount = 0;
        resolvedCount = 0;
        showLoading(true);
        String username = UserIdentity.getID(this);
        tcpClient.send("REPORTS::GET_MINE::" + username);
    }

    private void updateChipCounts() {
        chipAll.setText("All (" + allReports.size() + ")");
        chipActive.setText("Active (" + activeCount + ")");
        chipResolved.setText("Resolved (" + resolvedCount + ")");
    }

    private void displayFilteredReports() {
        reportsContainer.removeAllViews();

        List<ReportItem> filtered = new ArrayList<>();
        for (ReportItem report : allReports) {
            if (currentFilter.equals("ALL") ||
                    (currentFilter.equals("ACTIVE") && report.isActive()) ||
                    (currentFilter.equals("RESOLVED") && report.isResolved())) {
                filtered.add(report);
            }
        }

        if (filtered.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
        } else {
            emptyState.setVisibility(View.GONE);
            for (ReportItem report : filtered) {
                addReportCard(report);
            }
        }
    }

    private void addReportCard(ReportItem report) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View cardView = inflater.inflate(R.layout.item_my_report_card, reportsContainer, false);

        TextView tvTypeTag = cardView.findViewById(R.id.cardTypeTag);
        TextView tvStatusTag = cardView.findViewById(R.id.cardStatusTag);
        TextView tvTitle = cardView.findViewById(R.id.cardTitle);
        TextView tvSubtitle = cardView.findViewById(R.id.cardSubtitle);
        TextView tvDate = cardView.findViewById(R.id.cardDate);
        MaterialButton btnResolve = cardView.findViewById(R.id.btnResolve);
        MaterialButton btnDelete = cardView.findViewById(R.id.btnDelete);
        LinearLayout actionButtons = cardView.findViewById(R.id.actionButtons);

        // Set type badge
        if (report.isLost) {
            tvTypeTag.setText("LOST");
            tvTypeTag.setTextColor(getColor(R.color.lost_text));
            tvTypeTag.setBackgroundResource(R.drawable.bg_badge_lost);
        } else {
            tvTypeTag.setText("FOUND");
            tvTypeTag.setTextColor(getColor(R.color.found_text));
            tvTypeTag.setBackgroundResource(R.drawable.bg_badge_found);
        }

        // Set status badge
        if (report.isResolved()) {
            tvStatusTag.setText("RESOLVED");
            tvStatusTag.setTextColor(getColor(R.color.status_resolved_text));
            tvStatusTag.setBackgroundResource(R.drawable.bg_badge_resolved);
            btnResolve.setVisibility(View.GONE);
        } else {
            tvStatusTag.setText("ACTIVE");
            tvStatusTag.setTextColor(getColor(R.color.status_active_text));
            tvStatusTag.setBackgroundResource(R.drawable.bg_badge_active);
            btnResolve.setVisibility(View.VISIBLE);
        }

        tvTitle.setText(report.name);
        tvDate.setText(formatDate(report.reportDate));

        // Build subtitle
        StringBuilder subtitle = new StringBuilder();
        if (report.category != null && !report.category.isEmpty()) {
            subtitle.append(report.category);
        }
        if (report.color != null && !report.color.isEmpty()) {
            if (subtitle.length() > 0) subtitle.append(" • ");
            subtitle.append(report.color);
        }
        if (report.location != null && !report.location.isEmpty()) {
            if (subtitle.length() > 0) subtitle.append(" • ");
            subtitle.append(report.location);
        }
        tvSubtitle.setText(subtitle.length() > 0 ? subtitle.toString() : "No details");

        // Mark Resolved button
        btnResolve.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Mark as Resolved")
                    .setMessage("Are you sure you want to mark this item as resolved? This indicates the item has been found/returned.")
                    .setPositiveButton("Yes, Resolve", (dialog, which) -> {
                        String username = UserIdentity.getID(this);
                        tcpClient.send("REPORT::RESOLVE::" + report.id + "::" + username);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // Delete button
        btnDelete.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Delete Report")
                    .setMessage("Are you sure you want to delete this report? This action cannot be undone.")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        String username = UserIdentity.getID(this);
                        tcpClient.send("REPORT::DELETE::" + report.id + "::" + username);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // Card click to view details
        cardView.setOnClickListener(v -> {
            Intent i = new Intent(this, ReportDetailActivity.class);
            i.putExtra("id", report.id);
            i.putExtra("ownerId", report.ownerId);
            i.putExtra("type", report.isLost ? "LOST" : "FOUND");
            i.putExtra("name", report.name);
            i.putExtra("category", report.category);
            i.putExtra("color", report.color);
            i.putExtra("location", report.location);
            i.putExtra("description", report.description);
            i.putExtra("reportDate", report.reportDate);
            i.putExtra("status", report.status);
            startActivity(i);
        });

        reportsContainer.addView(cardView);
    }

    private String formatDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";
        try {
            // Parse YYYY-MM-DD format and return shorter format
            String[] parts = dateStr.split("-");
            if (parts.length == 3) {
                String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
                int monthIndex = Integer.parseInt(parts[1]) - 1;
                if (monthIndex >= 0 && monthIndex < 12) {
                    return months[monthIndex] + " " + Integer.parseInt(parts[2]);
                }
            }
        } catch (Exception e) {
            // Fall through
        }
        return dateStr;
    }

    private void showLoading(boolean show) {
        loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            reportsContainer.removeAllViews();
            emptyState.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpClient != null) tcpClient.close();
    }
}