package com.example.lostfound;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText etEmail, etPassword;
    private TcpClient tcpClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (UserIdentity.isLoggedIn(this)) {
            launchMain();
            return;
        }

        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);

        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnSignup = findViewById(R.id.btnSignup);

        // CONNECT ONCE
        tcpClient = new TcpClient(ServerConfig.SERVER_IP, ServerConfig.SERVER_PORT, this::handleServerResponse);
        tcpClient.connect();

        btnLogin.setOnClickListener(v -> {
            String user = getText(etEmail);
            String pass = getText(etPassword);

            Log.d("AUTH_UI", "LOGIN clicked user=" + user);
            Toast.makeText(this, "LOGIN clicked", Toast.LENGTH_SHORT).show();

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please enter username & password", Toast.LENGTH_SHORT).show();
                return;
            }

            tcpClient.send("AUTH::LOGIN::" + user + "::" + pass);
        });

        btnSignup.setOnClickListener(v -> {
            String user = getText(etEmail);
            String pass = getText(etPassword);

            Log.d("AUTH_UI", "SIGNUP clicked user=" + user);
            Toast.makeText(this, "SIGNUP clicked", Toast.LENGTH_SHORT).show();

            if (user.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Please enter username & password", Toast.LENGTH_SHORT).show();
                return;
            }

            // IMPORTANT: SIGNUP command
            tcpClient.send("AUTH::SIGNUP::" + user + "::" + pass);
        });
    }

    private String getText(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void handleServerResponse(String message) {
        runOnUiThread(() -> {
            String clean = (message == null) ? "" : message.trim();
            Log.d("AUTH_NET", "Server: " + clean);

            if (clean.startsWith("AUTH_SUCCESS")) {
                String[] parts = clean.split("::");
                String username = parts.length > 1 ? parts[1] : "";
                String phone = parts.length > 2 ? parts[2] : "";
                String line = parts.length > 3 ? parts[3] : "";

                UserIdentity.saveLoginInfo(this, username, phone, line);
                Toast.makeText(this, "Success: " + username, Toast.LENGTH_SHORT).show();
                launchMain();
                return;
            }

            if (clean.startsWith("AUTH_FAIL")) {
                Toast.makeText(this, clean, Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this, clean, Toast.LENGTH_SHORT).show();
        });
    }

    private void launchMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpClient != null) tcpClient.close();
    }
}
