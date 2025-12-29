package com.example.lostfound;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TcpClient tcpClient;
    private ExtendedFloatingActionButton fabReport;
    private ActivityResultLauncher<Intent> reportLauncher;

    private LinearLayout lostContainer, foundContainer;
    private Chip chipAll, chipLost, chipFound;
    private int lostCount = 0;
    private int foundCount = 0;

    // Search UI
    private EditText searchInput;
    private LinearLayout searchResultsSection, searchResultsContainer, emptySearchState;
    private LinearLayout normalFeedSection;
    private TextView searchResultsTitle, btnClearSearch;
    private HorizontalScrollView categoryScrollView;

    // Category chips
    private Chip chipCatAll, chipCatElectronics, chipCatWallet, chipCatKeys, chipCatIdCard, chipCatBag, chipCatOther;

    // Search state
    private boolean isSearchMode = false;
    private String currentSearchQuery = "";
    private String currentTypeFilter = "*"; // *, LOST, FOUND
    private String currentCategoryFilter = "*";
    private final List<ReportItem> searchResults = new ArrayList<>();

    // Debounce handler for search
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // Dedupe
    private final HashSet<String> seenReports = new HashSet<>();
    private boolean initialLoadDone = false;

    // Notifications
    private View notificationBadge;
    private FrameLayout btnNotifications;

    // Matches
    private View matchesBadge;
    private FrameLayout btnMatches;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!UserIdentity.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        // Normal feed views
        lostContainer = findViewById(R.id.lostFeedContainer);
        foundContainer = findViewById(R.id.foundFeedContainer);
        fabReport = findViewById(R.id.fab_report);
        normalFeedSection = findViewById(R.id.normalFeedSection);

        // Type filter chips
        chipAll = findViewById(R.id.chipAll);
        chipLost = findViewById(R.id.chipLost);
        chipFound = findViewById(R.id.chipFound);

        // Search UI
        searchInput = findViewById(R.id.searchInput);
        searchResultsSection = findViewById(R.id.searchResultsSection);
        searchResultsContainer = findViewById(R.id.searchResultsContainer);
        emptySearchState = findViewById(R.id.emptySearchState);
        searchResultsTitle = findViewById(R.id.searchResultsTitle);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        categoryScrollView = findViewById(R.id.categoryScrollView);

        // Category chips
        chipCatAll = findViewById(R.id.chipCatAll);
        chipCatElectronics = findViewById(R.id.chipCatElectronics);
        chipCatWallet = findViewById(R.id.chipCatWallet);
        chipCatKeys = findViewById(R.id.chipCatKeys);
        chipCatIdCard = findViewById(R.id.chipCatIdCard);
        chipCatBag = findViewById(R.id.chipCatBag);
        chipCatOther = findViewById(R.id.chipCatOther);

        setupSearchListeners();
        setupFilterChips();
        setupCategoryChips();

        // Setup notifications
        btnNotifications = findViewById(R.id.btnNotifications);
        notificationBadge = findViewById(R.id.notificationBadge);
        btnNotifications.setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class));
        });

        // Setup matches
        btnMatches = findViewById(R.id.btnMatches);
        matchesBadge = findViewById(R.id.matchesBadge);
        btnMatches.setOnClickListener(v -> {
            startActivity(new Intent(this, MatchesActivity.class));
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            if (id == R.id.nav_chat) {
                startActivity(new Intent(this, ChatListActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
            if (id == R.id.nav_my_reports) {
                startActivity(new Intent(this, MyReportsActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }
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

        tcpClient = new TcpClient(ServerConfig.SERVER_IP, ServerConfig.SERVER_PORT, msg -> runOnUiThread(() -> onServerMessage(msg)));
        tcpClient.connect();

        fabReport.setOnClickListener(v -> reportLauncher.launch(new Intent(MainActivity.this, Report.class)));
    }

    private void setupSearchListeners() {
        // Text change listener with debounce
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();

                // Cancel previous search request
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                if (query.isEmpty() && currentTypeFilter.equals("*") && currentCategoryFilter.equals("*")) {
                    // No search criteria, show normal feed
                    exitSearchMode();
                    return;
                }

                // Debounce: wait 500ms before searching
                searchRunnable = () -> {
                    currentSearchQuery = query;
                    performSearch();
                };
                searchHandler.postDelayed(searchRunnable, 500);
            }
        });

        // Handle keyboard search action
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                currentSearchQuery = searchInput.getText().toString().trim();
                performSearch();
                return true;
            }
            return false;
        });

        // Clear search button
        btnClearSearch.setOnClickListener(v -> {
            searchInput.setText("");
            currentSearchQuery = "";
            currentTypeFilter = "*";
            currentCategoryFilter = "*";
            updateTypeChipStyles();
            updateCategoryChipStyles();
            exitSearchMode();
        });
    }

    private void setupFilterChips() {
        chipAll.setOnClickListener(v -> {
            currentTypeFilter = "*";
            updateTypeChipStyles();
            if (isSearchMode || !currentSearchQuery.isEmpty() || !currentCategoryFilter.equals("*")) {
                performSearch();
            }
        });

        chipLost.setOnClickListener(v -> {
            currentTypeFilter = "LOST";
            updateTypeChipStyles();
            enterSearchMode();
            performSearch();
        });

        chipFound.setOnClickListener(v -> {
            currentTypeFilter = "FOUND";
            updateTypeChipStyles();
            enterSearchMode();
            performSearch();
        });
    }

    private void setupCategoryChips() {
        View.OnClickListener categoryClickListener = v -> {
            if (v == chipCatAll) currentCategoryFilter = "*";
            else if (v == chipCatElectronics) currentCategoryFilter = "Electronics";
            else if (v == chipCatWallet) currentCategoryFilter = "Wallet";
            else if (v == chipCatKeys) currentCategoryFilter = "Keys";
            else if (v == chipCatIdCard) currentCategoryFilter = "ID Card";
            else if (v == chipCatBag) currentCategoryFilter = "Bag";
            else if (v == chipCatOther) currentCategoryFilter = "Other";

            updateCategoryChipStyles();
            performSearch();
        };

        chipCatAll.setOnClickListener(categoryClickListener);
        chipCatElectronics.setOnClickListener(categoryClickListener);
        chipCatWallet.setOnClickListener(categoryClickListener);
        chipCatKeys.setOnClickListener(categoryClickListener);
        chipCatIdCard.setOnClickListener(categoryClickListener);
        chipCatBag.setOnClickListener(categoryClickListener);
        chipCatOther.setOnClickListener(categoryClickListener);
    }

    private void updateTypeChipStyles() {
        // Reset all
        chipAll.setChipBackgroundColorResource(R.color.surface_white);
        chipAll.setTextColor(getColor(R.color.text_main));
        chipLost.setChipBackgroundColorResource(R.color.surface_white);
        chipLost.setTextColor(getColor(R.color.text_main));
        chipFound.setChipBackgroundColorResource(R.color.surface_white);
        chipFound.setTextColor(getColor(R.color.text_main));

        // Highlight selected
        Chip selected;
        switch (currentTypeFilter) {
            case "LOST": selected = chipLost; break;
            case "FOUND": selected = chipFound; break;
            default: selected = chipAll;
        }
        selected.setChipBackgroundColorResource(R.color.primary);
        selected.setTextColor(getColor(R.color.surface_white));
    }

    private void updateCategoryChipStyles() {
        Chip[] chips = {chipCatAll, chipCatElectronics, chipCatWallet, chipCatKeys, chipCatIdCard, chipCatBag, chipCatOther};
        String[] values = {"*", "Electronics", "Wallet", "Keys", "ID Card", "Bag", "Other"};

        for (int i = 0; i < chips.length; i++) {
            if (currentCategoryFilter.equals(values[i])) {
                chips[i].setChipBackgroundColorResource(R.color.primary);
                chips[i].setTextColor(getColor(R.color.surface_white));
                chips[i].setChipStrokeColorResource(R.color.primary);
            } else {
                chips[i].setChipBackgroundColorResource(R.color.surface_white);
                chips[i].setTextColor(getColor(R.color.text_main));
                chips[i].setChipStrokeColorResource(R.color.divider);
            }
        }
    }

    private void enterSearchMode() {
        if (!isSearchMode) {
            isSearchMode = true;
            normalFeedSection.setVisibility(View.GONE);
            searchResultsSection.setVisibility(View.VISIBLE);
            categoryScrollView.setVisibility(View.VISIBLE);
        }
    }

    private void exitSearchMode() {
        isSearchMode = false;
        searchResultsSection.setVisibility(View.GONE);
        categoryScrollView.setVisibility(View.GONE);
        normalFeedSection.setVisibility(View.VISIBLE);
        searchResults.clear();
        searchResultsContainer.removeAllViews();
    }

    private void performSearch() {
        enterSearchMode();
        searchResults.clear();
        searchResultsContainer.removeAllViews();
        emptySearchState.setVisibility(View.GONE);

        // Build search query: SEARCH::query::category::type::location::date_from::date_to
        String query = currentSearchQuery.isEmpty() ? "*" : currentSearchQuery;
        String searchMessage = "SEARCH::" + query + "::" + currentCategoryFilter + "::" +
                currentTypeFilter + "::*::*::*";

        if (tcpClient != null) {
            tcpClient.send(searchMessage);
        }
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

        // Check for unread notifications
        if (tcpClient != null) {
            String username = UserIdentity.getID(this);
            if (username != null) {
                tcpClient.send("NOTIFICATIONS::UNREAD_COUNT::" + username);
            }
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

        // Handle normal feed messages
        if (msg.equals("REPORTS_DONE")) {
            initialLoadDone = true;
            return;
        }

        if (msg.startsWith("REPORT::") && !isSearchMode) {
            // Dedupe for normal feed
            if (seenReports.contains(msg)) return;
            seenReports.add(msg);
            addCardToFeed(msg);
            return;
        }

        // Handle search results
        if (msg.startsWith("SEARCH_RESULT::")) {
            ReportItem report = ReportItem.fromServerString(msg);
            if (report != null) {
                searchResults.add(report);
                addSearchResultCard(report);
            }
            return;
        }

        if (msg.startsWith("SEARCH_DONE::")) {
            String[] parts = msg.split("::");
            int count = parts.length > 1 ? Integer.parseInt(parts[1]) : searchResults.size();
            searchResultsTitle.setText("Search Results (" + count + ")");

            if (count == 0) {
                emptySearchState.setVisibility(View.VISIBLE);
            } else {
                emptySearchState.setVisibility(View.GONE);
            }
            return;
        }

        // Handle report updates
        if (msg.startsWith("REPORT_DELETED::") || msg.startsWith("REPORT_RESOLVED::")) {
            // Refresh the feed if we get update notifications
            if (!isSearchMode) {
                resetFeedAndDedupe();
                tcpClient.send("REPORTS::GET");
            }
            return;
        }

        // Handle notification unread count
        if (msg.startsWith("NOTIFICATIONS::UNREAD_COUNT::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 3) {
                try {
                    int count = Integer.parseInt(parts[2]);
                    notificationBadge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                } catch (NumberFormatException e) {
                    notificationBadge.setVisibility(View.GONE);
                }
            }
            return;
        }
    }

    private void addSearchResultCard(ReportItem report) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View cardView = inflater.inflate(R.layout.item_card, null, false);

        TextView tvTag = cardView.findViewById(R.id.cardTag);
        TextView tvTitle = cardView.findViewById(R.id.cardTitle);
        TextView tvSubtitle = cardView.findViewById(R.id.cardSubtitle);
        TextView tvUploader = cardView.findViewById(R.id.cardUploader);
        ImageView btnEdit = cardView.findViewById(R.id.btnEdit);
        ImageView cardPhoto = cardView.findViewById(R.id.cardPhoto);

        tvTitle.setText(report.name);
        tvUploader.setText("Posted by: " + report.ownerId);

        String subtitle = report.category;
        if (subtitle == null || subtitle.isEmpty()) subtitle = "Category";
        if (report.color != null && !report.color.isEmpty()) subtitle += " • " + report.color;
        if (report.location != null && !report.location.isEmpty()) subtitle += " • " + report.location;
        tvSubtitle.setText(subtitle);

        // Hide edit button in search results
        btnEdit.setVisibility(View.GONE);

        // Load photo if available
        if (report.hasPhoto()) {
            cardPhoto.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(report.photoUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_search_rounded)
                    .into(cardPhoto);

            cardPhoto.setOnClickListener(v -> {
                Intent photoIntent = new Intent(this, PhotoViewActivity.class);
                photoIntent.putExtra("photoUrl", report.photoUrl);
                photoIntent.putExtra("title", report.name);
                startActivity(photoIntent);
            });
        } else {
            cardPhoto.setVisibility(View.GONE);
        }

        if (report.isLost) {
            tvTag.setText("LOST");
            tvTag.setTextColor(getColor(R.color.lost_text));
            tvTag.setBackgroundResource(R.drawable.bg_badge_lost);
        } else {
            tvTag.setText("FOUND");
            tvTag.setTextColor(getColor(R.color.found_text));
            tvTag.setBackgroundResource(R.drawable.bg_badge_found);
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
            i.putExtra("status", report.status);
            i.putExtra("photoUrl", report.photoUrl);
            startActivity(i);
        });

        searchResultsContainer.addView(cardView);
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
        ImageView cardPhoto = cardView.findViewById(R.id.cardPhoto);

        tvTitle.setText(report.name);
        tvUploader.setText("Posted by: " + report.ownerId);

        String subtitle = report.category;
        if (subtitle == null || subtitle.isEmpty()) subtitle = "Category";
        if (report.color != null && !report.color.isEmpty()) subtitle += " • " + report.color;
        if (report.location != null && !report.location.isEmpty()) subtitle += " • " + report.location;
        tvSubtitle.setText(subtitle);

        // Load photo if available
        if (report.hasPhoto()) {
            cardPhoto.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(report.photoUrl)
                    .centerCrop()
                    .placeholder(R.drawable.bg_search_rounded)
                    .into(cardPhoto);

            cardPhoto.setOnClickListener(v -> {
                Intent photoIntent = new Intent(this, PhotoViewActivity.class);
                photoIntent.putExtra("photoUrl", report.photoUrl);
                photoIntent.putExtra("title", report.name);
                startActivity(photoIntent);
            });
        } else {
            cardPhoto.setVisibility(View.GONE);
        }

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
            i.putExtra("photoUrl", report.photoUrl);
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
