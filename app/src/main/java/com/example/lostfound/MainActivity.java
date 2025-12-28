package com.example.lostfound;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private TcpClient tcpClient;
    private ExtendedFloatingActionButton fabReport;
    private ActivityResultLauncher<Intent> reportLauncher;

    // Containers for the split feed
    private LinearLayout lostContainer;
    private LinearLayout foundContainer;

    // Chips and Counters for dynamic numbers
    private Chip chipLost, chipFound;
    private int lostCount = 0;
    private int foundCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Bind Views
        lostContainer = findViewById(R.id.lostFeedContainer);
        foundContainer = findViewById(R.id.foundFeedContainer);
        fabReport = findViewById(R.id.fab_report);
        chipLost = findViewById(R.id.chipLost);
        chipFound = findViewById(R.id.chipFound);

        // 2. Setup Activity Result Launcher (Receives data from ReportActivity)
        reportLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String message = result.getData().getStringExtra("REPORT_MESSAGE");
                        if (message != null) {
                            tcpClient.send(message);
                        }
                    }
                }
        );

        // 3. Setup TCP Client
        // Note: Use 10.0.2.2 for Emulator, or your PC IP for real device
        tcpClient = new TcpClient("10.0.2.2", 5000, message -> {
            runOnUiThread(() -> addCardToFeed(message));
        });

        // Connect in background
        new Thread(() -> tcpClient.connect()).start();

        // 4. Setup Button Click
        fabReport.setOnClickListener(v -> {
            // This launches your Activity named Report
            Intent intent = new Intent(MainActivity.this, Report.class);
            reportLauncher.launch(intent);
        });
    }

    private void addCardToFeed(String message) {

        ReportItem report = ReportItem.fromServerString(message);

        LayoutInflater inflater = LayoutInflater.from(this);
        // Using the item_card template we created earlier
        View cardView = inflater.inflate(R.layout.item_card, null, false);

        TextView tvTag = cardView.findViewById(R.id.cardTag);
        TextView tvTitle = cardView.findViewById(R.id.cardTitle);
        TextView tvSubtitle = cardView.findViewById(R.id.cardSubtitle);

        LinearLayout targetContainer = lostContainer; // Default fallback

        if (message.contains("[LOST]")) {
            // -- Style for LOST --
            tvTag.setText("LOST");
            tvTag.setTextColor(getResources().getColor(R.color.lost_text));
            tvTag.setBackgroundResource(R.drawable.bg_badge_lost);

            targetContainer = lostContainer;

            // Update Counter
            lostCount++;
            chipLost.setText("Lost (" + lostCount + ")");

        } else if (message.contains("[FOUND]")) {
            // -- Style for FOUND --
            tvTag.setText("FOUND");
            tvTag.setTextColor(getResources().getColor(R.color.found_text));
            tvTag.setBackgroundResource(R.drawable.bg_badge_found);

            targetContainer = foundContainer;

            // Update Counter
            foundCount++;
            chipFound.setText("Found (" + foundCount + ")");
        }

        // Clean up text
        String cleanMessage = message.replace("[LOST]", "").replace("[FOUND]", "").trim();
        tvTitle.setText(cleanMessage);
        tvSubtitle.setText("Just now • Campus");

        // Add the card to the TOP of the correct list
        targetContainer.addView(cardView, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpClient != null) {
            tcpClient.close();
        }
    }
}