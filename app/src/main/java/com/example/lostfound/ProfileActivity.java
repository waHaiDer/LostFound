package com.example.lostfound;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvPhone, tvLine;
    private TcpClient tcpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!UserIdentity.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_profile);

        TextView tvUsername = findViewById(R.id.etUsername);
        tvPhone = findViewById(R.id.etPhone);
        tvLine = findViewById(R.id.etLine);

        Button btnLogout = findViewById(R.id.btnLogout);
        Button btnEdit = findViewById(R.id.btnEdit);

        String username = UserIdentity.getID(this);
        tvUsername.setText(username);
        updateDisplay();

        btnEdit.setOnClickListener(v -> showEditDialog(username));

        btnLogout.setOnClickListener(v -> {
            UserIdentity.logout(this);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.nav_profile);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_profile) return true;

            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                overridePendingTransition(0, 0);
                return true;
            }

            if (id == R.id.nav_my_reports) {
                startActivity(new Intent(this, Report.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                overridePendingTransition(0, 0);
                return true;
            }

            return true;
        });
    }

    private void showEditDialog(String username) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Contact Info");

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
        final EditText etNewPhone = view.findViewById(R.id.etEditPhone);
        final EditText etNewLine = view.findViewById(R.id.etEditLine);

        etNewPhone.setText(UserIdentity.getPhone(this));
        etNewLine.setText(UserIdentity.getLineId(this));

        builder.setView(view);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newPhone = etNewPhone.getText().toString().trim();
            String newLine = etNewLine.getText().toString().trim();
            sendUpdateToServer(username, newPhone, newLine);
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void sendUpdateToServer(String username, String newPhone, String newLine) {
        new Thread(() -> {
            tcpClient = new TcpClient("10.0.2.2", 12345, response -> runOnUiThread(() -> {
                String r = response.trim();

                if (r.startsWith("UPDATE_SUCCESS")) {
                    UserIdentity.saveLoginInfo(this, username, newPhone, newLine);
                    updateDisplay();
                    Toast.makeText(this, "Profile Updated!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Update Failed: " + r, Toast.LENGTH_SHORT).show();
                }

                if (tcpClient != null) tcpClient.close();
            }));

            tcpClient.connect();
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}

            tcpClient.send("AUTH::UPDATE::" + username + "::" + newPhone + "::" + newLine);
        }).start();
    }

    private void updateDisplay() {
        String phone = UserIdentity.getPhone(this);
        String line = UserIdentity.getLineId(this);

        tvPhone.setText(phone == null || phone.isEmpty() ? "No Phone Set" : phone);
        tvLine.setText(line == null || line.isEmpty() ? "No LINE ID Set" : line);
    }
}
