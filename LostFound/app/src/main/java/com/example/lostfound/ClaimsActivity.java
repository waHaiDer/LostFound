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
import java.util.stream.Collectors;

public class ClaimsActivity extends AppCompatActivity {

    private TcpClient tcpClient;
    private String reportId;
    private String reportName;
    private String currentUser;

    private RecyclerView claimsRecyclerView;
    private ProgressBar progressBar;
    private LinearLayout emptyState;
    private TextView tvEmptyMessage, tvReportName;

    private Chip chipAll, chipPending, chipApproved, chipRejected;

    private final List<Claim> allClaims = new ArrayList<>();
    private final List<Claim> filteredClaims = new ArrayList<>();
    private ClaimAdapter adapter;
    private String currentFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_claims);

        reportId = getIntent().getStringExtra("reportId");
        reportName = getIntent().getStringExtra("reportName");
        currentUser = UserIdentity.getID(this);

        initViews();
        setupFilters();
        setupRecyclerView();
        connectAndLoad();
    }

    private void initViews() {
        claimsRecyclerView = findViewById(R.id.claimsRecyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        tvReportName = findViewById(R.id.tvReportName);

        chipAll = findViewById(R.id.chipAll);
        chipPending = findViewById(R.id.chipPending);
        chipApproved = findViewById(R.id.chipApproved);
        chipRejected = findViewById(R.id.chipRejected);

        tvReportName.setText(reportName != null ? reportName : "");

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
        adapter = new ClaimAdapter();
        claimsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        claimsRecyclerView.setAdapter(adapter);
    }

    private void connectAndLoad() {
        progressBar.setVisibility(View.VISIBLE);

        tcpClient = new TcpClient(ServerConfig.SERVER_IP, ServerConfig.SERVER_PORT, msg -> runOnUiThread(() -> onServerMessage(msg)));
        tcpClient.connect();

        new android.os.Handler().postDelayed(() -> {
            tcpClient.getClaimsForReport(reportId);
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

        // Question sent
        if (msg.startsWith("CLAIM_QUESTION_SENT::")) {
            Toast.makeText(this, "Question sent to claimer", Toast.LENGTH_SHORT).show();
            return;
        }

        // Claim approved
        if (msg.startsWith("CLAIM_APPROVED::")) {
            Toast.makeText(this, "Claim approved!", Toast.LENGTH_SHORT).show();
            refreshClaims();
            return;
        }

        // Claim rejected
        if (msg.startsWith("CLAIM_REJECTED::")) {
            Toast.makeText(this, "Claim rejected", Toast.LENGTH_SHORT).show();
            refreshClaims();
            return;
        }

        // Errors
        if (msg.startsWith("CLAIM_APPROVE_FAIL::") || msg.startsWith("CLAIM_REJECT_FAIL::") || msg.startsWith("CLAIM_QUESTION_FAIL::")) {
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
            tvEmptyMessage.setText(currentFilter.equals("ALL") ? "No claims yet" : "No " + currentFilter.toLowerCase() + " claims");
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
        tcpClient.getClaimsForReport(reportId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpClient != null) tcpClient.close();
    }

    // ---- Claim Adapter ----
    private class ClaimAdapter extends RecyclerView.Adapter<ClaimAdapter.ClaimViewHolder> {

        @NonNull
        @Override
        public ClaimViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_claim, parent, false);
            return new ClaimViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ClaimViewHolder holder, int position) {
            Claim claim = filteredClaims.get(position);
            holder.bind(claim);
        }

        @Override
        public int getItemCount() {
            return filteredClaims.size();
        }

        class ClaimViewHolder extends RecyclerView.ViewHolder {
            TextView tvClaimerInitial, tvClaimerName, tvClaimDate, tvClaimStatus;
            TextView tvProofDescription, tvReportName;
            LinearLayout actionButtons, claimerActionButtons;
            Button btnReject, btnAskQuestion, btnApprove;

            ClaimViewHolder(@NonNull View itemView) {
                super(itemView);
                tvClaimerInitial = itemView.findViewById(R.id.tvClaimerInitial);
                tvClaimerName = itemView.findViewById(R.id.tvClaimerName);
                tvClaimDate = itemView.findViewById(R.id.tvClaimDate);
                tvClaimStatus = itemView.findViewById(R.id.tvClaimStatus);
                tvProofDescription = itemView.findViewById(R.id.tvProofDescription);
                tvReportName = itemView.findViewById(R.id.tvReportName);
                actionButtons = itemView.findViewById(R.id.actionButtons);
                claimerActionButtons = itemView.findViewById(R.id.claimerActionButtons);
                btnReject = itemView.findViewById(R.id.btnReject);
                btnAskQuestion = itemView.findViewById(R.id.btnAskQuestion);
                btnApprove = itemView.findViewById(R.id.btnApprove);
            }

            void bind(Claim claim) {
                tvClaimerName.setText(claim.claimerUsername);
                tvClaimDate.setText(formatDate(claim.createdAt));
                tvProofDescription.setText(claim.proofDescription);
                tvClaimStatus.setText(claim.getStatusDisplayText());

                // Status badge color
                GradientDrawable statusBg = (GradientDrawable) tvClaimStatus.getBackground();
                statusBg.setColor(claim.getStatusColor());

                // Avatar
                String initial = claim.claimerUsername != null && !claim.claimerUsername.isEmpty()
                        ? claim.claimerUsername.substring(0, 1).toUpperCase()
                        : "?";
                tvClaimerInitial.setText(initial);
                GradientDrawable avatarBg = (GradientDrawable) tvClaimerInitial.getBackground();
                avatarBg.setColor(getAvatarColor(claim.claimerUsername));

                // Hide report name in finder view
                tvReportName.setVisibility(View.GONE);

                // Show action buttons only for pending claims
                claimerActionButtons.setVisibility(View.GONE);
                if (claim.isPending()) {
                    actionButtons.setVisibility(View.VISIBLE);

                    btnReject.setOnClickListener(v -> showRejectDialog(claim));
                    btnAskQuestion.setOnClickListener(v -> showQuestionDialog(claim));
                    btnApprove.setOnClickListener(v -> showApproveConfirmation(claim));
                } else {
                    actionButtons.setVisibility(View.GONE);
                }

                // Click to open chat with claimer
                itemView.setOnClickListener(v -> {
                    Intent intent = new Intent(ClaimsActivity.this, ChatRoomActivity.class);
                    intent.putExtra("otherUser", claim.claimerUsername);
                    intent.putExtra("reportId", reportId);
                    startActivity(intent);
                });
            }

            private void showRejectDialog(Claim claim) {
                AlertDialog.Builder builder = new AlertDialog.Builder(ClaimsActivity.this);
                builder.setTitle("Reject Claim");

                EditText input = new EditText(ClaimsActivity.this);
                input.setHint("Reason for rejection (optional)");
                input.setPadding(48, 32, 48, 32);
                builder.setView(input);

                builder.setPositiveButton("Reject", (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    if (reason.isEmpty()) reason = "No reason provided";
                    tcpClient.rejectClaim(claim.id, currentUser, reason);
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            }

            private void showQuestionDialog(Claim claim) {
                AlertDialog.Builder builder = new AlertDialog.Builder(ClaimsActivity.this);
                builder.setTitle("Ask Verification Question");

                EditText input = new EditText(ClaimsActivity.this);
                input.setHint("e.g., What color case was on the phone?");
                input.setPadding(48, 32, 48, 32);
                builder.setView(input);

                builder.setPositiveButton("Send", (dialog, which) -> {
                    String question = input.getText().toString().trim();
                    if (question.isEmpty()) {
                        Toast.makeText(ClaimsActivity.this, "Please enter a question", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    tcpClient.askClaimQuestion(claim.id, currentUser, question);
                });
                builder.setNegativeButton("Cancel", null);
                builder.show();
            }

            private void showApproveConfirmation(Claim claim) {
                new AlertDialog.Builder(ClaimsActivity.this)
                        .setTitle("Approve Claim")
                        .setMessage("Are you sure you want to approve this claim? This will mark the item as resolved and reject other pending claims.")
                        .setPositiveButton("Approve", (dialog, which) -> {
                            tcpClient.approveClaim(claim.id, currentUser);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
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
