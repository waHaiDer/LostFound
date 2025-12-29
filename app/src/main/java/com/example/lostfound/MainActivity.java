package com.example.lostfound;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.HashSet;

public class MainActivity extends AppCompatActivity {

    private TcpClient tcpClient;
    private ExtendedFloatingActionButton fabReport;
    private ActivityResultLauncher<Intent> reportLauncher;

    private LinearLayout lostContainer, foundContainer;
    private Chip chipLost, chipFound;
    private int lostCount = 0;
    private int foundCount = 0;

    // ✅ Dedupe
    private final HashSet<String> seenReports = new HashSet<>();
    private boolean initialLoadDone = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!UserIdentity.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        lostContainer = findViewById(R.id.lostFeedContainer);
        foundContainer = findViewById(R.id.foundFeedContainer);
        fabReport = findViewById(R.id.fab_report);
        chipLost = findViewById(R.id.chipLost);
        chipFound = findViewById(R.id.chipFound);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            return true;
        });

        reportLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String payload = result.getData().getStringExtra("REPORT_MESSAGE");
                        if (payload == null || payload.trim().isEmpty()) return;

                        String currentUser = UserIdentity.getID(this);
                        if (currentUser == null) return;

                        String fullMessage = "REPORT::" + currentUser + "::" + payload;
                        if (tcpClient != null) tcpClient.send(fullMessage);
                    }
                }
        );

        tcpClient = new TcpClient("10.0.2.2", 12345, msg -> runOnUiThread(() -> onServerMessage(msg)));
        tcpClient.connect();

        fabReport.setOnClickListener(v -> reportLauncher.launch(new Intent(MainActivity.this, Report.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();

        // ✅ Only load once per app session, or you can force refresh by setting initialLoadDone=false when needed.
        if (!initialLoadDone && tcpClient != null) {
            resetFeedAndDedupe();
            android.util.Log.d("MAIN", "Sending REPORTS::GET (initialLoadDone=" + initialLoadDone + ")");
            tcpClient.send("REPORTS::GET");
        }
    }

    private void resetFeedAndDedupe() {
        lostContainer.removeAllViews();
        foundContainer.removeAllViews();
        lostCount = 0;
        foundCount = 0;
        chipLost.setText("Lost (0)");
        chipFound.setText("Found (0)");
        seenReports.clear();
    }

    private void onServerMessage(String raw) {
        String msg = raw == null ? "" : raw.trim();
        android.util.Log.d("MAIN", "From server: " + raw);
        if (msg.equals("REPORTS_DONE")) {
            initialLoadDone = true;
            return;
        }

        if (msg.startsWith("REPORT::")) {
            // ✅ DEDUPE
            if (seenReports.contains(msg)) return;
            seenReports.add(msg);

            addCardToFeed(msg);
            return;
        }

        // Optional debugging:
        // Toast.makeText(this, "Server: " + msg, Toast.LENGTH_SHORT).show();
    }

    private void addCardToFeed(String rawMessage) {
        ReportItem report = ReportItem.fromServerString(rawMessage);
        if (report == null) return;


        LayoutInflater inflater = LayoutInflater.from(this);
        View cardView = inflater.inflate(R.layout.item_card, null, false);

        TextView tvTag = cardView.findViewById(R.id.cardTag);
        TextView tvTitle = cardView.findViewById(R.id.cardTitle);
        TextView tvSubtitle = cardView.findViewById(R.id.cardSubtitle);
        TextView tvUploader = cardView.findViewById(R.id.cardUploader);
        ImageView btnEdit = cardView.findViewById(R.id.btnEdit);

        tvTitle.setText(report.name);
        tvUploader.setText("Posted by: " + report.ownerId);

        String subtitle = report.category;
        if (subtitle == null || subtitle.isEmpty()) subtitle = "Category";
        if (report.color != null && !report.color.isEmpty()) subtitle += " • " + report.color;
        if (report.location != null && !report.location.isEmpty()) subtitle += " • " + report.location;
        tvSubtitle.setText(subtitle);

        String currentUser = UserIdentity.getID(this);
        if (currentUser != null && currentUser.equals(report.ownerId)) {
            btnEdit.setVisibility(View.VISIBLE);
            btnEdit.setOnClickListener(v -> Toast.makeText(this, "Edit feature coming soon!", Toast.LENGTH_SHORT).show());
        } else {
            btnEdit.setVisibility(View.GONE);
        }

        cardView.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, ReportDetailActivity.class);
            i.putExtra("id", report.id);
            i.putExtra("ownerId", report.ownerId);
            i.putExtra("type", report.isLost ? "LOST" : "FOUND");
            i.putExtra("name", report.name);
            i.putExtra("category", report.category);
            i.putExtra("color", report.color);
            i.putExtra("location", report.location);
            i.putExtra("description", report.description);
            i.putExtra("reportDate", report.reportDate);
            startActivity(i);
        });


        LinearLayout targetContainer;
        if (report.isLost) {
            tvTag.setText("LOST");
            tvTag.setTextColor(getColor(R.color.lost_text));
            tvTag.setBackgroundResource(R.drawable.bg_badge_lost);

            targetContainer = lostContainer;
            lostCount++;
            chipLost.setText("Lost (" + lostCount + ")");
        } else {
            tvTag.setText("FOUND");
            tvTag.setTextColor(getColor(R.color.found_text));
            tvTag.setBackgroundResource(R.drawable.bg_badge_found);

            targetContainer = foundContainer;
            foundCount++;
            chipFound.setText("Found (" + foundCount + ")");
        }

        targetContainer.addView(cardView, 0);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpClient != null) tcpClient.close();
    }
}
