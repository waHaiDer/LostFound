package com.example.lostfound;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class MyClaimsActivity extends AppCompatActivity {

    private TcpClient tcpClient;
    private String currentUser;

    private RecyclerView claimsRecyclerView;
    private ProgressBar progressBar;
    private LinearLayout emptyState;

    private Chip chipAll, chipPending, chipApproved, chipRejected;

    private final List<Claim> allClaims = new ArrayList<>();
    private final List<Claim> filteredClaims = new ArrayList<>();
    private MyClaimAdapter adapter;
    private String currentFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_claims);

        currentUser = UserIdentity.getID(this);

        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupFilters();
        setupRecyclerView();
        connectAndLoad();
    }

    private void initViews() {
        claimsRecyclerView = findViewById(R.id.claimsRecyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);

        chipAll = findViewById(R.id.chipAll);
        chipPending = findViewById(R.id.chipPending);
        chipApproved = findViewById(R.id.chipApproved);
        chipRejected = findViewById(R.id.chipRejected);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupFilters() {
        chipAll.setChecked(true);

        View.OnClickListener filterClick = v -> {
            if (v == chipAll) currentFilter = "ALL";
            else if (v == chipPending) currentFilter = "PENDING";
            else if (v == chipApproved) currentFilter = "APPROVED";
            else if (v == chipRejected) currentFilter = "REJECTED";
            applyFilter();
        };

        chipAll.setOnClickListener(filterClick);
        chipPending.setOnClickListener(filterClick);
        chipApproved.setOnClickListener(filterClick);
        chipRejected.setOnClickListener(filterClick);
    }

    private void setupRecyclerView() {
        adapter = new MyClaimAdapter();
        claimsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        claimsRecyclerView.setAdapter(adapter);
    }

    private void connectAndLoad() {
        progressBar.setVisibility(View.VISIBLE);

        tcpClient = new TcpClient(ServerConfig.SERVER_IP, ServerConfig.SERVER_PORT, msg -> runOnUiThread(() -> onServerMessage(msg)));
        tcpClient.connect();

        new android.os.Handler().postDelayed(() -> {
            tcpClient.getClaimsByUser(currentUser);
        }, 300);
    }

    private void onServerMessage(String raw) {
        String msg = raw == null ? "" : raw.trim();

        // Claim data
        if (msg.startsWith("CLAIM::")) {
            Claim claim = Claim.fromServerString(msg);
            if (claim != null) {
                allClaims.add(claim);
            }
            return;
        }

        // Claims done loading
        if (msg.equals("CLAIMS_DONE")) {
            progressBar.setVisibility(View.GONE);
            applyFilter();
            return;
        }

        // Answer sent
        if (msg.startsWith("CLAIM_ANSWER_SENT::")) {
            Toast.makeText(this, "Answer sent!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Claim cancelled
        if (msg.startsWith("CLAIM_CANCELLED::")) {
            Toast.makeText(this, "Claim cancelled", Toast.LENGTH_SHORT).show();
            refreshClaims();
            return;
        }

        // Real-time claim updates
        if (msg.startsWith("CLAIM::APPROVED::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 3) {
                Toast.makeText(this, "Your claim was approved!", Toast.LENGTH_LONG).show();
                refreshClaims();
            }
            return;
        }

        if (msg.startsWith("CLAIM::REJECTED::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 3) {
                Toast.makeText(this, "Your claim was rejected", Toast.LENGTH_LONG).show();
                refreshClaims();
            }
            return;
        }

        if (msg.startsWith("CLAIM::QUESTION::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 4) {
                String question = parts[3];
                Toast.makeText(this, "New question: " + question, Toast.LENGTH_LONG).show();
            }
            return;
        }

        // Errors
        if (msg.startsWith("CLAIM_CANCEL_FAIL::") || msg.startsWith("CLAIM_ANSWER_FAIL::")) {
            String[] parts = msg.split("::");
            String error = parts.length > 1 ? parts[1] : "Unknown error";
            Toast.makeText(this, "Error: " + error, Toast.LENGTH_SHORT).show();
        }
    }

    private void applyFilter() {
        filteredClaims.clear();

        for (Claim claim : allClaims) {
            if (currentFilter.equals("ALL")) {
                filteredClaims.add(claim);
            } else if (currentFilter.equals("PENDING") && claim.isPending()) {
                filteredClaims.add(claim);
            } else if (currentFilter.equals("APPROVED") && "APPROVED".equals(claim.status)) {
                filteredClaims.add(claim);
            } else if (currentFilter.equals("REJECTED") && "REJECTED".equals(claim.status)) {
                filteredClaims.add(claim);
            }
        }

        adapter.notifyDataSetChanged();

        if (filteredClaims.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            claimsRecyclerView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            claimsRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void refreshClaims() {
        allClaims.clear();
        filteredClaims.clear();
        adapter.notifyDataSetChanged();
        progressBar.setVisibility(View.VISIBLE);
        tcpClient.getClaimsByUser(currentUser);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpClient != null) tcpClient.close();
    }

    // ---- My Claim Adapter ----
    private class MyClaimAdapter extends RecyclerView.Adapter<MyClaimAdapter.MyClaimViewHolder> {

        @NonNull
        @Override
        public MyClaimViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_claim, parent, false);
            return new MyClaimViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MyClaimViewHolder holder, int position) {
            Claim claim = filteredClaims.get(position);
            holder.bind(claim);
        }

        @Override
        public int getItemCount() {
            return filteredClaims.size();
        }

        class MyClaimViewHolder extends RecyclerView.ViewHolder {
            TextView tvClaimerInitial, tvClaimerName, tvClaimDate, tvClaimStatus;
            TextView tvProofDescription, tvReportName;
            LinearLayout actionButtons, claimerActionButtons;
            Button btnCancel, btnAnswerQuestion;

            MyClaimViewHolder(@NonNull View itemView) {
                super(itemView);
                tvClaimerInitial = itemView.findViewById(R.id.tvClaimerInitial);
                tvClaimerName = itemView.findViewById(R.id.tvClaimerName);
                tvClaimDate = itemView.findViewById(R.id.tvClaimDate);
                tvClaimStatus = itemView.findViewById(R.id.tvClaimStatus);
                tvProofDescription = itemView.findViewById(R.id.tvProofDescription);
                tvReportName = itemView.findViewById(R.id.tvReportName);
                actionButtons = itemView.findViewById(R.id.actionButtons);
                claimerActionButtons = itemView.findViewById(R.id.claimerActionButtons);
                btnCancel = itemView.findViewById(R.id.btnCancel);
                btnAnswerQuestion = itemView.findViewById(R.id.btnAnswerQuestion);
            }

            void bind(Claim claim) {
                // Show report name instead of claimer name (since this is user's own claims)
                tvClaimerName.setText(claim.reportName != null ? claim.reportName : "Item");
                tvClaimDate.setText(formatDate(claim.createdAt));
                tvProofDescription.setText(claim.proofDescription);
                tvClaimStatus.setText(claim.getStatusDisplayText());

                // Status badge color
                GradientDrawable statusBg = (GradientDrawable) tvClaimStatus.getBackground();
                statusBg.setColor(claim.getStatusColor());

                // Avatar - use report owner initial
                String initial = claim.reportOwner != null && !claim.reportOwner.isEmpty()
                        ? claim.reportOwner.substring(0, 1).toUpperCase()
                        : "?";
                tvClaimerInitial.setText(initial);
                GradientDrawable avatarBg = (GradientDrawable) tvClaimerInitial.getBackground();
                avatarBg.setColor(getAvatarColor(claim.reportOwner));

                // Show finder info
                tvReportName.setVisibility(View.VISIBLE);
                tvReportName.setText("Found by: " + (claim.reportOwner != null ? claim.reportOwner : "Unknown"));

                // Hide finder action buttons, show claimer buttons
                actionButtons.setVisibility(View.GONE);

                if (claim.isPending()) {
                    claimerActionButtons.setVisibility(View.VISIBLE);
                    btnCancel.setOnClickListener(v -> showCancelConfirmation(claim));

                    // Show answer button if status is QUESTIONING
                    if ("QUESTIONING".equals(claim.status)) {
                        btnAnswerQuestion.setVisibility(View.VISIBLE);
                        btnAnswerQuestion.setOnClickListener(v -> showAnswerDialog(claim));
                    } else {
                        btnAnswerQuestion.setVisibility(View.GONE);
                    }
                } else {
                    claimerActionButtons.setVisibility(View.GONE);
                }

                // Click to open chat with finder
                itemView.setOnClickListener(v -> {
                    if (claim.reportOwner != null) {
                        Intent intent = new Intent(MyClaimsActivity.this, ChatRoomActivity.class);
                        intent.putExtra("otherUser", claim.reportOwner);
                        intent.putExtra("reportId", claim.reportId);
                        startActivity(intent);
                    }
                });
            }

            private void showCancelConfirmation(Claim claim) {
                new AlertDialog.Builder(MyClaimsActivity.this)
                        .setTitle("Cancel Claim")
                        .setMessage("Are you sure you want to cancel this claim?")
                        .setPositiveButton("Cancel Claim", (dialog, which) -> {
                            tcpClient.cancelClaim(claim.id, currentUser);
                        })
                        .setNegativeButton("Keep Claim", null)
                        .show();
            }

            private void showAnswerDialog(Claim claim) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MyClaimsActivity.this);
                builder.setTitle("Answer Verification Question");

                EditText input = new EditText(MyClaimsActivity.this);
                input.setHint("Your answer...");
                input.setPadding(48, 32, 48, 32);
                builder.setView(input);

                builder.setPositiveButton("Send", (dialog, which) -> {
                    String answer = input.getText().toString().trim();
                    if (answer.isEmpty()) {
                        Toast.makeText(MyClaimsActivity.this, "Please enter an answer", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    tcpClient.answerClaimQuestion(claim.id, currentUser, answer);
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            }

            private String formatDate(String timestamp) {
                if (timestamp == null || timestamp.isEmpty()) return "";
                try {
                    if (timestamp.contains(" ")) {
                        return timestamp.split(" ")[0];
                    }
                } catch (Exception e) {
                    // Ignore
                }
                return timestamp;
            }

            private int getAvatarColor(String username) {
                int[] colors = {
                        0xFF2196F3, 0xFF4CAF50, 0xFFFF9800, 0xFF9C27B0,
                        0xFFE91E63, 0xFF00BCD4, 0xFF795548, 0xFF607D8B
                };
                int hash = username == null ? 0 : username.hashCode();
                return colors[Math.abs(hash) % colors.length];
            }
        }
    }
}
