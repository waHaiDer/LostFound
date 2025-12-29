package com.example.lostfound;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

/**
 * Activity for real-time location sharing between two users.
 * Sprint 5 - Location Sharing feature (Epic 10).
 */
public class LocationSharingActivity extends AppCompatActivity {

    private static final String TAG = "LocationSharing";
    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final long LOCATION_UPDATE_INTERVAL = 5000; // 5 seconds
    private static final long SESSION_TIMEOUT = 30 * 60 * 1000; // 30 minutes

    // Views
    private MapView mapView;
    private TextView locationTitle;
    private TextView locationSubtitle;
    private TextView distanceText;
    private TextView etaText;
    private TextView accuracyText;
    private Button btnStopSharing;
    private LinearLayout waitingOverlay;
    private ProgressBar waitingProgress;
    private TextView waitingText;
    private LinearLayout invitationButtons;
    private Button btnAccept;
    private Button btnDecline;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    // Map markers
    private Marker myMarker;
    private Marker otherMarker;

    // Session data
    private TcpClient tcpClient;
    private String currentUser;
    private String otherUser;
    private String sessionId;
    private boolean isInitiator;
    private boolean isSessionActive = false;
    private LocationSession locationSession;

    // Timeout handler
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize OSMDroid configuration
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_location_sharing);

        // Check login
        if (!UserIdentity.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        // Get intent data
        currentUser = UserIdentity.getID(this);
        otherUser = getIntent().getStringExtra("otherUser");
        sessionId = getIntent().getStringExtra("sessionId");
        isInitiator = getIntent().getBooleanExtra("isInitiator", false);

        if (otherUser == null || sessionId == null) {
            Toast.makeText(this, "Invalid session data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        locationSession = new LocationSession(sessionId, currentUser, otherUser);

        initViews();
        setupMap();
        setupLocationClient();
        connectToServer();

        if (isInitiator) {
            // Show waiting overlay while waiting for acceptance
            showWaitingState("Waiting for " + otherUser + " to accept...");
            // Send session start request
            tcpClient.startLocationSession(sessionId, currentUser, otherUser);
        } else {
            // Show invitation acceptance UI
            showInvitationState("Accept location sharing with " + otherUser + "?");
        }
    }

    private void initViews() {
        mapView = findViewById(R.id.mapView);
        locationTitle = findViewById(R.id.locationTitle);
        locationSubtitle = findViewById(R.id.locationSubtitle);
        distanceText = findViewById(R.id.distanceText);
        etaText = findViewById(R.id.etaText);
        accuracyText = findViewById(R.id.accuracyText);
        btnStopSharing = findViewById(R.id.btnStopSharing);
        waitingOverlay = findViewById(R.id.waitingOverlay);
        waitingProgress = findViewById(R.id.waitingProgress);
        waitingText = findViewById(R.id.waitingText);
        invitationButtons = findViewById(R.id.invitationButtons);
        btnAccept = findViewById(R.id.btnAccept);
        btnDecline = findViewById(R.id.btnDecline);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> confirmExit());

        locationSubtitle.setText("Sharing with " + otherUser);

        btnStopSharing.setOnClickListener(v -> stopSharing());

        btnAccept.setOnClickListener(v -> acceptInvitation());
        btnDecline.setOnClickListener(v -> declineInvitation());
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);

        IMapController mapController = mapView.getController();
        mapController.setZoom(15.0);

        // Create markers
        myMarker = new Marker(mapView);
        myMarker.setTitle("You");
        myMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        otherMarker = new Marker(mapView);
        otherMarker.setTitle(otherUser);
        otherMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);

        // Set different colors for markers
        Drawable myIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_mylocation);
        Drawable otherIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_mapmode);

        if (myIcon != null) {
            myIcon.setTint(ContextCompat.getColor(this, R.color.primary));
            myMarker.setIcon(myIcon);
        }

        if (otherIcon != null) {
            otherIcon.setTint(ContextCompat.getColor(this, R.color.lost_text));
            otherMarker.setIcon(otherIcon);
        }
    }

    private void setupLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_UPDATE_INTERVAL)
                .setMinUpdateIntervalMillis(LOCATION_UPDATE_INTERVAL / 2)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null && isSessionActive) {
                    updateMyLocation(location);
                }
            }
        };
    }

    private void connectToServer() {
        tcpClient = new TcpClient(ServerConfig.SERVER_IP, ServerConfig.SERVER_PORT, msg -> runOnUiThread(() -> onServerMessage(msg)));
        tcpClient.connect();
        tcpClient.send("REGISTER_SOCKET::" + currentUser);
    }

    private void onServerMessage(String raw) {
        String msg = raw == null ? "" : raw.trim();
        Log.d(TAG, "Received: " + msg);

        // Session started
        if (msg.startsWith("LOCATION::SESSION_STARTED::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 3 && parts[2].equals(sessionId)) {
                onSessionStarted();
            }
            return;
        }

        // Session invitation (for non-initiator)
        if (msg.startsWith("LOCATION::SESSION_INVITE::")) {
            // Already handled via intent, but could receive real-time
            return;
        }

        // Session declined
        if (msg.startsWith("LOCATION::SESSION_DECLINED::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 3 && parts[2].equals(sessionId)) {
                Toast.makeText(this, otherUser + " declined the location sharing request", Toast.LENGTH_LONG).show();
                finish();
            }
            return;
        }

        // Location update from other user
        if (msg.startsWith("LOCATION::UPDATE::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 7 && parts[2].equals(sessionId)) {
                String username = parts[3];
                if (!username.equals(currentUser)) {
                    try {
                        double lat = Double.parseDouble(parts[4]);
                        double lng = Double.parseDouble(parts[5]);
                        float accuracy = Float.parseFloat(parts[6]);
                        updateOtherLocation(lat, lng, accuracy);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "Invalid location data", e);
                    }
                }
            }
            return;
        }

        // Session ended
        if (msg.startsWith("LOCATION::SESSION_ENDED::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 3 && parts[2].equals(sessionId)) {
                onSessionEnded(false);
            }
            return;
        }
    }

    private void showWaitingState(String message) {
        waitingOverlay.setVisibility(View.VISIBLE);
        waitingProgress.setVisibility(View.VISIBLE);
        waitingText.setText(message);
        invitationButtons.setVisibility(View.GONE);
        btnStopSharing.setVisibility(View.GONE);
    }

    private void showInvitationState(String message) {
        waitingOverlay.setVisibility(View.VISIBLE);
        waitingProgress.setVisibility(View.GONE);
        waitingText.setText(message);
        invitationButtons.setVisibility(View.VISIBLE);
        btnStopSharing.setVisibility(View.GONE);
    }

    private void hideWaitingState() {
        waitingOverlay.setVisibility(View.GONE);
        btnStopSharing.setVisibility(View.VISIBLE);
    }

    private void acceptInvitation() {
        tcpClient.acceptLocationSession(sessionId, currentUser);
        showWaitingState("Starting session...");
    }

    private void declineInvitation() {
        tcpClient.declineLocationSession(sessionId, currentUser);
        finish();
    }

    private void onSessionStarted() {
        isSessionActive = true;
        locationSession.status = "ACTIVE";
        locationSession.startTime = System.currentTimeMillis();

        hideWaitingState();
        startLocationUpdates();
        startSessionTimeout();

        Toast.makeText(this, "Location sharing started!", Toast.LENGTH_SHORT).show();
    }

    private void startLocationUpdates() {
        if (!checkLocationPermission()) {
            requestLocationPermission();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        }
    }

    private void stopLocationUpdates() {
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle("Location Permission Needed")
                    .setMessage("This feature requires location access to share your position with " + otherUser + " for coordinating the item handoff.")
                    .setPositiveButton("Grant", (dialog, which) -> {
                        ActivityCompat.requestPermissions(this,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                LOCATION_PERMISSION_REQUEST);
                    })
                    .setNegativeButton("Cancel", (dialog, which) -> {
                        Toast.makeText(this, "Location permission required for this feature", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void updateMyLocation(Location location) {
        locationSession.myLatitude = location.getLatitude();
        locationSession.myLongitude = location.getLongitude();
        locationSession.myAccuracy = location.getAccuracy();

        // Update marker
        GeoPoint myPosition = new GeoPoint(location.getLatitude(), location.getLongitude());
        myMarker.setPosition(myPosition);

        if (!mapView.getOverlays().contains(myMarker)) {
            mapView.getOverlays().add(myMarker);
        }

        // Send update to server
        tcpClient.sendLocationUpdate(sessionId, currentUser,
                location.getLatitude(), location.getLongitude(), location.getAccuracy());

        // Update accuracy display
        accuracyText.setText(String.format("%.0f m", location.getAccuracy()));

        updateMapBounds();
        updateDistanceInfo();
    }

    private void updateOtherLocation(double lat, double lng, float accuracy) {
        locationSession.otherLatitude = lat;
        locationSession.otherLongitude = lng;
        locationSession.otherAccuracy = accuracy;

        // Update marker
        GeoPoint otherPosition = new GeoPoint(lat, lng);
        otherMarker.setPosition(otherPosition);

        if (!mapView.getOverlays().contains(otherMarker)) {
            mapView.getOverlays().add(otherMarker);
        }

        updateMapBounds();
        updateDistanceInfo();
    }

    private void updateMapBounds() {
        if (locationSession.myLatitude == 0 || locationSession.otherLatitude == 0) {
            // Only one location available, center on it
            if (locationSession.myLatitude != 0) {
                mapView.getController().animateTo(new GeoPoint(locationSession.myLatitude, locationSession.myLongitude));
            } else if (locationSession.otherLatitude != 0) {
                mapView.getController().animateTo(new GeoPoint(locationSession.otherLatitude, locationSession.otherLongitude));
            }
            return;
        }

        // Both locations available, zoom to fit both
        double north = Math.max(locationSession.myLatitude, locationSession.otherLatitude);
        double south = Math.min(locationSession.myLatitude, locationSession.otherLatitude);
        double east = Math.max(locationSession.myLongitude, locationSession.otherLongitude);
        double west = Math.min(locationSession.myLongitude, locationSession.otherLongitude);

        // Add padding
        double latPadding = (north - south) * 0.3;
        double lngPadding = (east - west) * 0.3;

        if (latPadding < 0.001) latPadding = 0.001;
        if (lngPadding < 0.001) lngPadding = 0.001;

        BoundingBox boundingBox = new BoundingBox(
                north + latPadding,
                east + lngPadding,
                south - latPadding,
                west - lngPadding
        );

        mapView.zoomToBoundingBox(boundingBox, true);
        mapView.invalidate();
    }

    private void updateDistanceInfo() {
        distanceText.setText(locationSession.getDistanceString());
        etaText.setText(locationSession.getEstimatedTime());
    }

    private void startSessionTimeout() {
        timeoutHandler = new Handler(Looper.getMainLooper());
        timeoutRunnable = () -> {
            Toast.makeText(this, "Location sharing session expired (30 min limit)", Toast.LENGTH_LONG).show();
            stopSharing();
        };
        timeoutHandler.postDelayed(timeoutRunnable, SESSION_TIMEOUT);
    }

    private void stopSessionTimeout() {
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
    }

    private void stopSharing() {
        if (isSessionActive) {
            tcpClient.endLocationSession(sessionId, currentUser);
        }
        onSessionEnded(true);
    }

    private void onSessionEnded(boolean byMe) {
        isSessionActive = false;
        locationSession.status = "ENDED";

        stopLocationUpdates();
        stopSessionTimeout();

        if (!byMe) {
            Toast.makeText(this, otherUser + " stopped sharing location", Toast.LENGTH_SHORT).show();
        }

        finish();
    }

    private void confirmExit() {
        if (isSessionActive) {
            new AlertDialog.Builder(this)
                    .setTitle("Stop Sharing?")
                    .setMessage("Are you sure you want to stop sharing your location?")
                    .setPositiveButton("Stop", (dialog, which) -> stopSharing())
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        confirmExit();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (isSessionActive) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        // Don't stop location updates when paused - keep sending updates
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        stopSessionTimeout();
        if (tcpClient != null) {
            if (isSessionActive) {
                tcpClient.endLocationSession(sessionId, currentUser);
            }
            tcpClient.close();
        }
    }
}
