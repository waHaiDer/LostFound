package com.example.lostfound;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.Locale;

public class ReportDetailActivity extends AppCompatActivity {

    private TcpClient tcpClient;

    private String reportId;
    private String ownerId;
    private boolean isOwner;

    private TextInputEditText etType, etName, etColor, etLocation, etDescription, etDate;
    private MaterialAutoCompleteTextView dropdownCategory;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_detail);

        // ---- Read Intent Extras ----
        reportId = getIntent().getStringExtra("id");
        ownerId  = getIntent().getStringExtra("ownerId");

        String currentUser = UserIdentity.getID(this);
        isOwner = currentUser != null && currentUser.equals(ownerId);

        // ---- Bind Views ----
        etType = findViewById(R.id.etType);
        etName = findViewById(R.id.etName);
        dropdownCategory = findViewById(R.id.dropdownCategory);
        etColor = findViewById(R.id.etColor);
        etLocation = findViewById(R.id.etLocation);
        etDescription = findViewById(R.id.etDescription);
        etDate = findViewById(R.id.etDate);
        btnSave = findViewById(R.id.btnSave);
        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // ---- Category Dropdown Setup ----
        String[] categories = {"Electronics", "Wallet", "Keys", "ID Card", "Bag", "Clothing", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categories);
        dropdownCategory.setAdapter(adapter);
        dropdownCategory.setOnClickListener(v -> dropdownCategory.showDropDown());
        dropdownCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) dropdownCategory.showDropDown();
        });

        // ---- Prefill ----
        etType.setText(getIntent().getStringExtra("type"));
        etName.setText(getIntent().getStringExtra("name"));

        String category = getIntent().getStringExtra("category");
        dropdownCategory.setText(category == null ? "" : category, false);

        etColor.setText(getIntent().getStringExtra("color"));
        etLocation.setText(getIntent().getStringExtra("location"));
        etDescription.setText(getIntent().getStringExtra("description"));
        etDate.setText(getIntent().getStringExtra("reportDate"));

        // ---- Date Picker (Owner only) ----
        etDate.setOnClickListener(v -> {
            if (!isOwner) return;
            openDatePicker();
        });

        // ---- View vs Edit ----
        setEditable(isOwner);
        btnSave.setEnabled(isOwner);
        btnSave.setAlpha(isOwner ? 1f : 0.35f);

        btnSave.setOnClickListener(v -> {
            if (!isOwner) return;
            saveUpdate();
        });
    }

    private void setEditable(boolean editable) {
        etType.setEnabled(false); // always read-only
        etName.setEnabled(editable);
        dropdownCategory.setEnabled(editable);
        etColor.setEnabled(editable);
        etLocation.setEnabled(editable);
        etDescription.setEnabled(editable);

        // date edits via picker
        etDate.setEnabled(editable);
        etDate.setFocusable(false);
        etDate.setClickable(true);
    }

    private void openDatePicker() {
        Calendar cal = Calendar.getInstance();

        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH);
        int d = cal.get(Calendar.DAY_OF_MONTH);

        // Use current value if it matches YYYY-MM-DD
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
        if (ownerId == null || ownerId.trim().isEmpty()) {
            Toast.makeText(this, "Missing owner id", Toast.LENGTH_SHORT).show();
            return;
        }

        String type = safe(etType); // LOST / FOUND
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
        if (date.isEmpty()) {
            Toast.makeText(this, "Date is required", Toast.LENGTH_SHORT).show();
            return;
        }

        String isLost = type.equalsIgnoreCase("LOST") ? "1" : "0";

        // REPORT::UPDATE::id::owner::is_lost::name::category::color::location::description::report_date
        String msg = "REPORT::UPDATE::" + reportId + "::" + ownerId + "::" + isLost
                + "::" + name
                + "::" + category
                + "::" + color
                + "::" + location
                + "::" + description
                + "::" + date;

        tcpClient = new TcpClient("10.0.2.2", 12345, response -> runOnUiThread(() -> {
            String clean = response == null ? "" : response.trim();
            if (clean.startsWith("UPDATE_OK")) {
                Toast.makeText(this, "Updated!", Toast.LENGTH_SHORT).show();
                finish();
            } else if (clean.startsWith("UPDATE_FAIL::NOT_OWNER")) {
                Toast.makeText(this, "You are not the owner of this report.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Update failed: " + clean, Toast.LENGTH_SHORT).show();
            }
            if (tcpClient != null) tcpClient.close();
        }));

        tcpClient.connect();
        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        tcpClient.send(msg);
    }

    private String safe(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
