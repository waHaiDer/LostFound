package com.example.lostfound;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import android.widget.ArrayAdapter;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

public class Report extends AppCompatActivity {

    private MaterialButtonToggleGroup toggleGroup;
    private TextInputEditText etItemName, etColor, etDescription, etLocation, etDate;
    private MaterialAutoCompleteTextView dropdownCategory;

    private String reportType = ""; // "LOST" or "FOUND"

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        // ===== Bind =====
        toggleGroup = findViewById(R.id.toggleGroup);
        Button btnLost = findViewById(R.id.btnOptionLost);
        Button btnFound = findViewById(R.id.btnOptionFound);

        etItemName = findViewById(R.id.etItemName);
        dropdownCategory = findViewById(R.id.dropdownCategory);
        etColor = findViewById(R.id.etColor);

        etDescription = findViewById(R.id.etDescription);
        etLocation = findViewById(R.id.etLocation);
        etDate = findViewById(R.id.etDate);

        Button btnSubmit = findViewById(R.id.btnSubmitReport);
        TextView btnCancel = findViewById(R.id.btnCancel);

        // ===== Cancel =====
        btnCancel.setOnClickListener(v -> finish());

        // ===== Toggle LOST/FOUND =====
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnOptionLost) reportType = "LOST";
            else if (checkedId == R.id.btnOptionFound) reportType = "FOUND";
        });

        // ===== Category Dropdown Setup =====
        String[] categories = {"Electronics", "Wallet", "Keys", "ID Card", "Bag", "Clothing", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categories);
        dropdownCategory.setAdapter(adapter);
        dropdownCategory.setText("Electronics", false);

        // Force dropdown to open reliably
        dropdownCategory.setOnClickListener(v -> dropdownCategory.showDropDown());
        dropdownCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) dropdownCategory.showDropDown();
        });

        // ===== Date Picker Setup =====
        etDate.setOnClickListener(v -> openDatePicker());

        // ===== Submit =====
        btnSubmit.setOnClickListener(v -> submitReport());
    }

    private void openDatePicker() {
        Calendar cal = Calendar.getInstance();
        int y = cal.get(Calendar.YEAR);
        int m = cal.get(Calendar.MONTH);
        int d = cal.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(
                Report.this,
                (view, year, month, dayOfMonth) -> {
                    String date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                    etDate.setText(date);
                },
                y, m, d
        );
        dialog.show();
    }

    private void submitReport() {
        if (reportType.isEmpty()) {
            Toast.makeText(this, "Please select Lost or Found", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = text(etItemName);
        String category = dropdownCategory.getText() == null ? "" : dropdownCategory.getText().toString().trim();
        String color = text(etColor);
        String location = text(etLocation);
        String description = text(etDescription);

        if (name.isEmpty()) {
            Toast.makeText(this, "Item name is required", Toast.LENGTH_SHORT).show();
            return;
        }

        // NOTE: your DB schema doesn’t store date yet; we collect it for UI, but do not send to server unless you add column.
        // If you want to store it later, we’ll extend protocol + DB.

        // Payload expected by MainActivity/server pipeline:
        // LOST/FOUND::name::category::color::location::description
        String payload = reportType
                + "::" + name
                + "::" + category
                + "::" + color
                + "::" + location
                + "::" + description;

        Intent data = new Intent();
        data.putExtra("REPORT_MESSAGE", payload);
        setResult(RESULT_OK, data);
        finish();
    }

    private String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
