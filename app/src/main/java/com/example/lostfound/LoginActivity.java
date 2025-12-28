package com.example.lostfound;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword; // We use etEmail for the Username
    private TcpClient tcpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Check if already logged in
        if (UserIdentity.isLoggedIn(this)) {
            launchMain();
            return;
        }

        setContentView(R.layout.activity_login);

        // 2. Link the UI elements
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnSignup = findViewById(R.id.btnSignup);

        // 3. Connect to Python Server (Emulator IP: 10.0.2.2)
        tcpClient = new TcpClient("10.0.2.2", 5000, this::handleServerResponse);
        tcpClient.connect();

        // 4. Login Button Logic
        btnLogin.setOnClickListener(v -> {
            String user = etEmail.getText().toString();
            String pass = etPassword.getText().toString();
            if (!user.isEmpty() && !pass.isEmpty()) {
                // SEND: AUTH::LOGIN::username::password
                tcpClient.send("AUTH::LOGIN::" + user + "::" + pass);
            }
        });

        // 5. Signup Button Logic
        btnSignup.setOnClickListener(v -> {
            String user = etEmail.getText().toString();
            String pass = etPassword.getText().toString();
            if (!user.isEmpty() && !pass.isEmpty()) {
                // SEND: AUTH::SIGNUP::username::password
                tcpClient.send("AUTH::SIGNUP::" + user + "::" + pass);
            }
        });
    }

    private void handleServerResponse(String message) {
        // 1. Run on UI Thread so we can change screens
        runOnUiThread(() -> {
            // 2. CLEAN the message (remove hidden spaces/newlines)
            String cleanMessage = message.trim();

            // Debug Popup: Prove the phone got it
            // Toast.makeText(this, "Server said: " + cleanMessage, Toast.LENGTH_SHORT).show();

            // 3. Check for Success
            if (cleanMessage.startsWith("AUTH_SUCCESS")) {
                String[] parts = cleanMessage.split("::");
                String userId = parts.length > 1 ? parts[1] : "UnknownUser";

                // 4. Save and Launch
                UserIdentity.saveID(this, userId);

                Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show();
                launchMain();

            } else if (cleanMessage.startsWith("AUTH_FAIL")) {
                Toast.makeText(this, "Login Failed: " + cleanMessage, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void launchMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}