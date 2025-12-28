package com.example.lostfound; // Note: lostfound (no 'and')

import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;

public class Report extends AppCompatActivity { // <--- THIS EXTENDS ACTIVITY

    private MaterialButtonToggleGroup toggleGroup;
    private MaterialButton btnOptionLost, btnOptionFound;
    private TextInputEditText etItemName, etColor, etDescription, etLocation, etDate;
    private AutoCompleteTextView dropdownCategory;
    private Button btnSubmit;
    private TextView btnCancel;

    // Image Upload
    private FrameLayout imageUploadContainer;
    private ImageView ivSelectedImage;
    private LinearLayout placeholderLayout;
    private Uri selectedImageUri;

    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report); // Points to your XML

        bindViews();
        setupDropdown();
        setupToggleLogic();
        setupImagePicker();
        setupDatePicker();

        btnCancel.setOnClickListener(v -> finish());
        btnSubmit.setOnClickListener(v -> submitReport());
    }

    private void bindViews() {
        toggleGroup = findViewById(R.id.toggleGroup);
        btnOptionLost = findViewById(R.id.btnOptionLost);
        btnOptionFound = findViewById(R.id.btnOptionFound);

        etItemName = findViewById(R.id.etItemName);
        etColor = findViewById(R.id.etColor);
        etDescription = findViewById(R.id.etDescription);
        etLocation = findViewById(R.id.etLocation);
        etDate = findViewById(R.id.etDate);

        dropdownCategory = findViewById(R.id.dropdownCategory);
        btnSubmit = findViewById(R.id.btnSubmitReport);
        btnCancel = findViewById(R.id.btnCancel);

        imageUploadContainer = findViewById(R.id.imageUploadContainer);
        ivSelectedImage = findViewById(R.id.ivSelectedImage);
        placeholderLayout = findViewById(R.id.placeholderLayout);
    }

    private void setupDropdown() {
        String[] categories = {"Electronics", "Clothing", "ID Cards", "Keys", "Books", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categories);
        dropdownCategory.setAdapter(adapter);
    }

    private void setupToggleLogic() {
        toggleGroup.check(R.id.btnOptionLost);
        updateToggleColors(R.id.btnOptionLost);

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                updateToggleColors(checkedId);
            }
        });
    }

    private void updateToggleColors(int checkedId) {
        if (checkedId == R.id.btnOptionLost) {
            btnOptionLost.setBackgroundColor(Color.parseColor("#2B7CEE")); // Blue
            btnOptionLost.setTextColor(Color.WHITE);
            btnOptionFound.setBackgroundColor(Color.TRANSPARENT);
            btnOptionFound.setTextColor(Color.parseColor("#475569"));
        } else if (checkedId == R.id.btnOptionFound) {
            btnOptionFound.setBackgroundColor(Color.parseColor("#2B7CEE")); // Blue
            btnOptionFound.setTextColor(Color.WHITE);
            btnOptionLost.setBackgroundColor(Color.TRANSPARENT);
            btnOptionLost.setTextColor(Color.parseColor("#475569"));
        }
    }

    private void setupDatePicker() {
        etDate.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, year1, month1, dayOfMonth) -> {
                        String date = dayOfMonth + "/" + (month1 + 1) + "/" + year1;
                        etDate.setText(date);
                    },
                    year, month, day
            );
            datePickerDialog.show();
        });
    }

    private void setupImagePicker() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        ivSelectedImage.setImageURI(uri);
                        ivSelectedImage.setVisibility(View.VISIBLE);
                        placeholderLayout.setVisibility(View.GONE);
                    }
                }
        );
        imageUploadContainer.setOnClickListener(v -> pickImageLauncher.launch("image/*"));
    }

    private void submitReport() {
        String name = etItemName.getText().toString().trim();
        String location = etLocation.getText().toString().trim();
        String color = etColor.getText().toString().trim();
        String category = dropdownCategory.getText().toString();
        String desc = etDescription.getText().toString().trim();
        String date = etDate.getText().toString().trim();
        boolean isLost = (toggleGroup.getCheckedButtonId() == R.id.btnOptionLost);

        if (name.isEmpty() || location.isEmpty()) {
            Toast.makeText(this, "Please fill in Item Name and Location", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean hasImage = (selectedImageUri != null);

        // HERE IS THE CHANGE: We use ReportItem now, not Report
        ReportItem newItem = new ReportItem(isLost, name, category, color, location, desc, date, hasImage);

        Intent resultIntent = new Intent();
        resultIntent.putExtra("REPORT_MESSAGE", newItem.toServerString());
        setResult(RESULT_OK, resultIntent);
        finish();
    }
}