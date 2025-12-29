package com.example.lostfound;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class MatchesActivity extends AppCompatActivity {

    private TcpClient tcpClient;
    private RecyclerView matchesRecyclerView;
    private TextView emptyText;
    private ProgressBar loadingProgress;

    private String currentUser;
    private final List<MatchItem> matches = new ArrayList<>();
    private MatchAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!UserIdentity.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_matches);

        currentUser = UserIdentity.getID(this);

        initViews();
        setupRecyclerView();
        connectAndLoad();
    }

    private void initViews() {
        matchesRecyclerView = findViewById(R.id.matchesRecyclerView);
        emptyText = findViewById(R.id.emptyText);
        loadingProgress = findViewById(R.id.loadingProgress);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }

    private void setupRecyclerView() {
        adapter = new MatchAdapter();
        matchesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        matchesRecyclerView.setAdapter(adapter);
    }

    private void connectAndLoad() {
        tcpClient = new TcpClient(ServerConfig.SERVER_IP, ServerConfig.SERVER_PORT, msg -> runOnUiThread(() -> onServerMessage(msg)));
        tcpClient.connect();
        tcpClient.send("MATCHES::GET::" + currentUser);
    }

    private void onServerMessage(String raw) {
        String msg = raw == null ? "" : raw.trim();

        if (msg.startsWith("MATCH::") && !msg.startsWith("MATCH::CONFIRMED") && !msg.startsWith("MATCH::DISMISSED")) {
            MatchItem match = MatchItem.fromServerString(msg);
            if (match != null) {
                matches.add(match);
                adapter.notifyItemInserted(matches.size() - 1);
            }
            return;
        }

        if (msg.equals("MATCHES_DONE")) {
            loadingProgress.setVisibility(View.GONE);
            if (matches.isEmpty()) {
                emptyText.setVisibility(View.VISIBLE);
                matchesRecyclerView.setVisibility(View.GONE);
            } else {
                emptyText.setVisibility(View.GONE);
                matchesRecyclerView.setVisibility(View.VISIBLE);
            }
            return;
        }

        if (msg.startsWith("MATCH_CONFIRMED::")) {
            String matchId = msg.split("::")[1];
            removeMatchFromList(matchId);
            Toast.makeText(this, "Match confirmed! You can now chat.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (msg.startsWith("MATCH_DISMISSED::")) {
            String matchId = msg.split("::")[1];
            removeMatchFromList(matchId);
            Toast.makeText(this, "Match dismissed", Toast.LENGTH_SHORT).show();
        }
    }

    private void removeMatchFromList(String matchId) {
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i).id.equals(matchId)) {
                matches.remove(i);
                adapter.notifyItemRemoved(i);
                break;
            }
        }

        if (matches.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            matchesRecyclerView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpClient != null) tcpClient.close();
    }

    // RecyclerView Adapter
    private class MatchAdapter extends RecyclerView.Adapter<MatchAdapter.MatchViewHolder> {

        @NonNull
        @Override
        public MatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_match, parent, false);
            return new MatchViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MatchViewHolder holder, int position) {
            MatchItem match = matches.get(position);
            holder.bind(match);
        }

        @Override
        public int getItemCount() {
            return matches.size();
        }

        class MatchViewHolder extends RecyclerView.ViewHolder {
            TextView confidenceBadge, scoreText;
            ImageView lostPhoto, foundPhoto;
            TextView lostName, lostOwner, foundName, foundOwner;
            Button btnDismiss, btnConfirm;

            MatchViewHolder(@NonNull View itemView) {
                super(itemView);
                confidenceBadge = itemView.findViewById(R.id.confidenceBadge);
                scoreText = itemView.findViewById(R.id.scoreText);
                lostPhoto = itemView.findViewById(R.id.lostPhoto);
                foundPhoto = itemView.findViewById(R.id.foundPhoto);
                lostName = itemView.findViewById(R.id.lostName);
                lostOwner = itemView.findViewById(R.id.lostOwner);
                foundName = itemView.findViewById(R.id.foundName);
                foundOwner = itemView.findViewById(R.id.foundOwner);
                btnDismiss = itemView.findViewById(R.id.btnDismiss);
                btnConfirm = itemView.findViewById(R.id.btnConfirm);
            }

            void bind(MatchItem match) {
                // Confidence badge
                confidenceBadge.setText(match.confidence);
                GradientDrawable background = new GradientDrawable();
                background.setCornerRadius(16f);
                background.setColor(Color.parseColor(match.getConfidenceColor()));
                confidenceBadge.setBackground(background);

                // Score
                scoreText.setText("Score: " + match.score + "%");

                // Lost item
                lostName.setText(match.lostItemName);
                lostOwner.setText("by: " + match.lostOwnerId);
                if (match.hasLostPhoto()) {
                    Glide.with(MatchesActivity.this)
                            .load(match.lostPhotoUrl)
                            .centerCrop()
                            .placeholder(R.drawable.bg_search_rounded)
                            .into(lostPhoto);
                } else {
                    lostPhoto.setImageResource(android.R.drawable.ic_menu_gallery);
                }

                // Found item
                foundName.setText(match.foundItemName);
                foundOwner.setText("by: " + match.foundOwnerId);
                if (match.hasFoundPhoto()) {
                    Glide.with(MatchesActivity.this)
                            .load(match.foundPhotoUrl)
                            .centerCrop()
                            .placeholder(R.drawable.bg_search_rounded)
                            .into(foundPhoto);
                } else {
                    foundPhoto.setImageResource(android.R.drawable.ic_menu_gallery);
                }

                // Actions
                btnConfirm.setOnClickListener(v -> {
                    tcpClient.send("MATCH::CONFIRM::" + match.id + "::" + currentUser);
                });

                btnDismiss.setOnClickListener(v -> {
                    tcpClient.send("MATCH::DISMISS::" + match.id + "::" + currentUser);
                });
            }
        }
    }
}
