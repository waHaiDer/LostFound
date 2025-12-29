package com.example.lostfound;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ReportDetailActivity extends AppCompatActivity {

    private TcpClient tcpClient;

    private String reportId;
    private String ownerId;
    private String reportType;
    private boolean isOwner;
    private String currentUser;

    private TextInputEditText etType, etName, etColor, etLocation, etDescription, etDate;
    private MaterialAutoCompleteTextView dropdownCategory;
    private Button btnSave, btnContact, btnClaim, btnViewClaims, btnSearchParty;
    private TextView tvOwnerBadge, tvCommentCount, tvNoComments;
    private RecyclerView commentsRecyclerView;
    private EditText etCommentInput;
    private ImageView btnSendComment;

    private final List<Comment> comments = new ArrayList<>();
    private CommentAdapter commentAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_detail);

        // ---- Read Intent Extras ----
        reportId = getIntent().getStringExtra("id");
        ownerId = getIntent().getStringExtra("ownerId");
        reportType = getIntent().getStringExtra("type");

        currentUser = UserIdentity.getID(this);
        isOwner = currentUser != null && currentUser.equals(ownerId);

        initViews();
        setupCategoryDropdown();
        prefillData();
        setupActionButtons();
        setupComments();
        connectAndLoad();
    }

    private void initViews() {
        // Form fields
        etType = findViewById(R.id.etType);
        etName = findViewById(R.id.etName);
        dropdownCategory = findViewById(R.id.dropdownCategory);
        etColor = findViewById(R.id.etColor);
        etLocation = findViewById(R.id.etLocation);
        etDescription = findViewById(R.id.etDescription);
        etDate = findViewById(R.id.etDate);

        // Action buttons
        btnSave = findViewById(R.id.btnSave);
        btnContact = findViewById(R.id.btnContact);
        btnClaim = findViewById(R.id.btnClaim);
        btnViewClaims = findViewById(R.id.btnViewClaims);
        btnSearchParty = findViewById(R.id.btnSearchParty);
        tvOwnerBadge = findViewById(R.id.tvOwnerBadge);

        // Comments
        tvCommentCount = findViewById(R.id.tvCommentCount);
        tvNoComments = findViewById(R.id.tvNoComments);
        commentsRecyclerView = findViewById(R.id.commentsRecyclerView);
        etCommentInput = findViewById(R.id.etCommentInput);
        btnSendComment = findViewById(R.id.btnSendComment);

        // Back button
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupCategoryDropdown() {
        String[] categories = {"Electronics", "Wallet", "Keys", "ID Card", "Bag", "Clothing", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categories);
        dropdownCategory.setAdapter(adapter);
        dropdownCategory.setOnClickListener(v -> dropdownCategory.showDropDown());
        dropdownCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) dropdownCategory.showDropDown();
        });
    }

    private void prefillData() {
        etType.setText(reportType);
        etName.setText(getIntent().getStringExtra("name"));

        String category = getIntent().getStringExtra("category");
        dropdownCategory.setText(category == null ? "" : category, false);

        etColor.setText(getIntent().getStringExtra("color"));
        etLocation.setText(getIntent().getStringExtra("location"));
        etDescription.setText(getIntent().getStringExtra("description"));
        etDate.setText(getIntent().getStringExtra("reportDate"));

        // Date picker for owner
        etDate.setOnClickListener(v -> {
            if (isOwner) openDatePicker();
        });

        // Set editable state
        setEditable(isOwner);
    }

    private void setupActionButtons() {
        if (isOwner) {
            // Owner can edit and save
            tvOwnerBadge.setVisibility(View.VISIBLE);
            btnSave.setVisibility(View.VISIBLE);
            btnSave.setOnClickListener(v -> saveUpdate());

            // Owner of FOUND items can view claims
            if ("FOUND".equalsIgnoreCase(reportType)) {
                btnViewClaims.setVisibility(View.VISIBLE);
                btnViewClaims.setOnClickListener(v -> openClaimsActivity());
            }

            // Owner of LOST items can start search party
            if ("LOST".equalsIgnoreCase(reportType)) {
                btnSearchParty.setVisibility(View.VISIBLE);
                btnSearchParty.setOnClickListener(v -> startSearchParty());
            }
        } else {
            // Non-owner can contact and claim
            btnContact.setVisibility(View.VISIBLE);
            btnContact.setOnClickListener(v -> openChat());

            // Can only claim FOUND items
            if ("FOUND".equalsIgnoreCase(reportType)) {
                btnClaim.setVisibility(View.VISIBLE);
                btnClaim.setOnClickListener(v -> showClaimDialog());
            }
        }
    }

    private void setupComments() {
        commentAdapter = new CommentAdapter();
        commentsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        commentsRecyclerView.setAdapter(commentAdapter);
        commentsRecyclerView.setNestedScrollingEnabled(false);

        btnSendComment.setOnClickListener(v -> postComment());
    }

    private void connectAndLoad() {
        tcpClient = new TcpClient(ServerConfig.SERVER_IP, ServerConfig.SERVER_PORT, msg -> runOnUiThread(() -> onServerMessage(msg)));
        tcpClient.connect();

        // Subscribe to report updates and load comments
        new android.os.Handler().postDelayed(() -> {
            if (currentUser != null) {
                tcpClient.subscribeToReport(reportId, currentUser);
            }
            tcpClient.getComments(reportId);
        }, 300);
    }

    private void onServerMessage(String raw) {
        String msg = raw == null ? "" : raw.trim();

        // Comment messages
        if (msg.startsWith("COMMENT::") && !msg.startsWith("COMMENT::NEW::") && !msg.startsWith("COMMENT::DELETED::")) {
            Comment comment = Comment.fromServerString(msg);
            if (comment != null) {
                comments.add(comment);
            }
            return;
        }

        // Comments done loading
        if (msg.startsWith("COMMENTS_DONE::")) {
            updateCommentsUI();
            return;
        }

        // New comment posted (real-time)
        if (msg.startsWith("COMMENT::NEW::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 7) {
                Comment c = new Comment();
                c.reportId = parts[2];
                c.id = parts[3];
                c.username = parts[4];
                c.text = parts[5];
                c.createdAt = parts[6];

                // Add if not already present
                boolean exists = false;
                for (Comment existing : comments) {
                    if (existing.id != null && existing.id.equals(c.id)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    comments.add(c);
                    updateCommentsUI();
                }
            }
            return;
        }

        // Comment deleted
        if (msg.startsWith("COMMENT::DELETED::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 4) {
                String deletedId = parts[3];
                comments.removeIf(c -> c.id != null && c.id.equals(deletedId));
                updateCommentsUI();
            }
            return;
        }

        // Comment posted confirmation
        if (msg.startsWith("COMMENT_POSTED::")) {
            etCommentInput.setText("");
            Toast.makeText(this, "Comment posted!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Comment delete confirmation
        if (msg.startsWith("COMMENT_DELETE_OK::")) {
            Toast.makeText(this, "Comment deleted", Toast.LENGTH_SHORT).show();
            return;
        }

        // Claim submitted
        if (msg.startsWith("CLAIM_SUBMITTED::")) {
            Toast.makeText(this, "Claim submitted successfully!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Claim error
        if (msg.startsWith("CLAIM_FAIL::")) {
            String[] parts = msg.split("::");
            String error = parts.length > 1 ? parts[1] : "Unknown error";
            Toast.makeText(this, "Claim failed: " + error, Toast.LENGTH_SHORT).show();
            return;
        }

        // Update response
        if (msg.startsWith("UPDATE_OK")) {
            Toast.makeText(this, "Updated!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (msg.startsWith("UPDATE_FAIL::NOT_OWNER")) {
            Toast.makeText(this, "You are not the owner of this report.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCommentsUI() {
        tvCommentCount.setText(String.valueOf(comments.size()));

        if (comments.isEmpty()) {
            tvNoComments.setVisibility(View.VISIBLE);
            commentsRecyclerView.setVisibility(View.GONE);
        } else {
            tvNoComments.setVisibility(View.GONE);
            commentsRecyclerView.setVisibility(View.VISIBLE);
            commentAdapter.notifyDataSetChanged();
        }
    }

    private void postComment() {
        String text = etCommentInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter a comment", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentUser == null) {
            Toast.makeText(this, "Please log in to comment", Toast.LENGTH_SHORT).show();
            return;
        }

        tcpClient.postComment(reportId, currentUser, text);
    }

    private void showClaimDialog() {
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to claim", Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Claim This Item");

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_claim, null);
        EditText etProof = dialogView.findViewById(R.id.etProof);

        builder.setView(dialogView);
        builder.setPositiveButton("Submit Claim", (dialog, which) -> {
            String proof = etProof.getText().toString().trim();
            if (proof.isEmpty()) {
                Toast.makeText(this, "Please describe how you can verify ownership", Toast.LENGTH_SHORT).show();
                return;
            }
            tcpClient.submitClaim(reportId, currentUser, proof);
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void openClaimsActivity() {
        Intent intent = new Intent(this, ClaimsActivity.class);
        intent.putExtra("reportId", reportId);
        intent.putExtra("reportName", safe(etName));
        startActivity(intent);
    }

    private void startSearchParty() {
        Intent intent = new Intent(this, SearchPartyActivity.class);
        intent.putExtra("sessionId", java.util.UUID.randomUUID().toString());
        intent.putExtra("reportId", reportId);
        intent.putExtra("itemName", safe(etName));
        intent.putExtra("isCreator", true);
        startActivity(intent);
    }

    private void openChat() {
        Intent intent = new Intent(this, ChatRoomActivity.class);
        intent.putExtra("otherUser", ownerId);
        intent.putExtra("reportId", reportId);
        startActivity(intent);
    }

    private void setEditable(boolean editable) {
        etType.setEnabled(false); // always read-only
        etName.setEnabled(editable);
        dropdownCategory.setEnabled(editable);
        etColor.setEnabled(editable);
        etLocation.setEnabled(editable);
        etDescription.setEnabled(editable);
        etDate.setEnabled(editable);
        etDate.setFocusable(false);
        etDate.setClickable(true);
    }

    private void openDatePicker() {
        Calendar cal = Calendar.getInstance();
        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH);
        int d = cal.get(Calendar.DAY_OF_MONTH);

        String cur = safe(etDate);
        try {
            if (cur.matches("\\d{4}-\\d{2}-\\d{2}")) {
                y = Integer.parseInt(cur.substring(0, 4));
                m = Integer.parseInt(cur.substring(5, 7)) - 1;
                d = Integer.parseInt(cur.substring(8, 10));
            }
        } catch (Exception ignored) {}

        new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    String date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    etDate.setText(date);
                },
                y, m, d
        ).show();
    }

    private void saveUpdate() {
        if (reportId == null || reportId.trim().isEmpty()) {
            Toast.makeText(this, "Missing report id", Toast.LENGTH_SHORT).show();
            return;
        }

        String type = safe(etType);
        String name = safe(etName);
        String category = dropdownCategory.getText() == null ? "" : dropdownCategory.getText().toString().trim();
        String color = safe(etColor);
        String location = safe(etLocation);
        String description = safe(etDescription);
        String date = safe(etDate);

        if (name.isEmpty()) {
            Toast.makeText(this, "Item name is required", Toast.LENGTH_SHORT).show();
            return;
        }

        String msg = "REPORT::UPDATE::" + reportId + "::" + ownerId + "::" + type
                + "::" + name + "::" + category + "::" + color
                + "::" + location + "::" + description + "::" + date;

        tcpClient.send(msg);
    }

    private String safe(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpClient != null) {
            if (currentUser != null) {
                tcpClient.unsubscribeFromReport(reportId, currentUser);
            }
            tcpClient.close();
        }
    }

    // ---- Comment Adapter ----
    private class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

        @NonNull
        @Override
        public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
            return new CommentViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
            Comment comment = comments.get(position);
            holder.bind(comment);
        }

        @Override
        public int getItemCount() {
            return comments.size();
        }

        class CommentViewHolder extends RecyclerView.ViewHolder {
            TextView tvUserInitial, tvUsername, tvTimestamp, tvCommentText;
            ImageView btnDeleteComment;

            CommentViewHolder(@NonNull View itemView) {
                super(itemView);
                tvUserInitial = itemView.findViewById(R.id.tvUserInitial);
                tvUsername = itemView.findViewById(R.id.tvUsername);
                tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
                tvCommentText = itemView.findViewById(R.id.tvCommentText);
                btnDeleteComment = itemView.findViewById(R.id.btnDeleteComment);
            }

            void bind(Comment comment) {
                tvUsername.setText(comment.username);
                tvCommentText.setText(comment.text);
                tvTimestamp.setText(comment.getFormattedTime());

                // Avatar initial
                String initial = comment.username != null && !comment.username.isEmpty()
                        ? comment.username.substring(0, 1).toUpperCase()
                        : "?";
                tvUserInitial.setText(initial);

                // Random avatar color based on username
                int color = getAvatarColor(comment.username);
                GradientDrawable bg = (GradientDrawable) tvUserInitial.getBackground();
                bg.setColor(color);

                // Show delete button for own comments
                if (currentUser != null && currentUser.equals(comment.username)) {
                    btnDeleteComment.setVisibility(View.VISIBLE);
                    btnDeleteComment.setOnClickListener(v -> confirmDeleteComment(comment));
                } else {
                    btnDeleteComment.setVisibility(View.GONE);
                }
            }

            private void confirmDeleteComment(Comment comment) {
                new AlertDialog.Builder(ReportDetailActivity.this)
                        .setTitle("Delete Comment")
                        .setMessage("Are you sure you want to delete this comment?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            tcpClient.deleteComment(comment.id, currentUser);
                            comments.remove(comment);
                            updateCommentsUI();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
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
