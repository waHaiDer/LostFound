package com.example.lostfound;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

public class Report extends AppCompatActivity {

    private MaterialButtonToggleGroup toggleGroup;
    private TextInputEditText etItemName, etColor, etDescription, etLocation, etDate;
    private MaterialAutoCompleteTextView dropdownCategory;
    private FrameLayout imageUploadContainer;
    private LinearLayout placeholderLayout;
    private ImageView ivSelectedImage;

    private String reportType = "";
    private Bitmap selectedBitmap = null;
    private Uri cameraImageUri = null;

    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        initViews();
        setupToggleGroup();
        setupCategoryDropdown();
        setupPhotoSelection();
        setupDatePicker();
        setupSubmitButton();
    }

    private void initViews() {
        toggleGroup = findViewById(R.id.toggleGroup);
        etItemName = findViewById(R.id.etItemName);
        dropdownCategory = findViewById(R.id.dropdownCategory);
        etColor = findViewById(R.id.etColor);
        etDescription = findViewById(R.id.etDescription);
        etLocation = findViewById(R.id.etLocation);
        etDate = findViewById(R.id.etDate);
        imageUploadContainer = findViewById(R.id.imageUploadContainer);
        placeholderLayout = findViewById(R.id.placeholderLayout);
        ivSelectedImage = findViewById(R.id.ivSelectedImage);

        TextView btnCancel = findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> finish());
    }

    private void setupToggleGroup() {
        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnOptionLost) reportType = "LOST";
            if (checkedId == R.id.btnOptionFound) reportType = "FOUND";
        });
    }

    private void setupCategoryDropdown() {
        String[] categories = {"Electronics", "Wallet", "Keys", "ID Card", "Bag", "Clothing", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categories);
        dropdownCategory.setAdapter(adapter);
        dropdownCategory.setText("Electronics", false);
        dropdownCategory.setOnClickListener(v -> dropdownCategory.showDropDown());
        dropdownCategory.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) dropdownCategory.showDropDown();
        });
    }

    private void setupPhotoSelection() {
        // Camera launcher
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && cameraImageUri != null) {
                        loadImage(cameraImageUri);
                    }
                }
        );

        // Gallery launcher
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            loadImage(uri);
                        }
                    }
                }
        );

        // Permission launcher
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        showPhotoOptionsDialog();
                    } else {
                        Toast.makeText(this, "Permission required for photos", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // Click listener for photo container
        imageUploadContainer.setOnClickListener(v -> {
            if (checkPermissions()) {
                showPhotoOptionsDialog();
            }
        });
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES);
                return false;
            }
        } else {
            // Android 12 and below
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                return false;
            }
        }
        return true;
    }

    private void showPhotoOptionsDialog() {
        String[] options = selectedBitmap == null ?
                new String[]{"Take Photo", "Choose from Gallery", "Cancel"} :
                new String[]{"Take Photo", "Choose from Gallery", "Remove Photo", "Cancel"};

        new AlertDialog.Builder(this)
                .setTitle("Add Photo")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Take Photo
                            openCamera();
                            break;
                        case 1: // Choose from Gallery
                            openGallery();
                            break;
                        case 2: // Remove Photo or Cancel
                            if (selectedBitmap != null) {
                                removePhoto();
                            }
                            break;
                    }
                })
                .show();
    }

    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }

        try {
            File photoFile = new File(getExternalCacheDir(), "camera_photo_" + System.currentTimeMillis() + ".jpg");
            cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photoFile);

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
            cameraLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open camera", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        galleryLauncher.launch(intent);
    }

    private void loadImage(Uri uri) {
        new Thread(() -> {
            Bitmap bitmap = ImageUtils.loadAndCompressBitmap(this, uri);
            runOnUiThread(() -> {
                if (bitmap != null) {
                    selectedBitmap = bitmap;
                    ivSelectedImage.setImageBitmap(bitmap);
                    ivSelectedImage.setVisibility(android.view.View.VISIBLE);
                    placeholderLayout.setVisibility(android.view.View.GONE);
                } else {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void removePhoto() {
        selectedBitmap = null;
        ivSelectedImage.setImageBitmap(null);
        ivSelectedImage.setVisibility(android.view.View.GONE);
        placeholderLayout.setVisibility(android.view.View.VISIBLE);
    }

    private void setupDatePicker() {
        etDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        String date = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
                        etDate.setText(date);
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
            ).show();
        });
    }

    private void setupSubmitButton() {
        Button btnSubmit = findViewById(R.id.btnSubmitReport);
        btnSubmit.setOnClickListener(v -> submit());
    }

    private void submit() {
        if (reportType.isEmpty()) {
            Toast.makeText(this, "Select Lost or Found", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = text(etItemName);
        String category = dropdownCategory.getText() == null ? "" : dropdownCategory.getText().toString().trim();
        String color = text(etColor);
        String location = text(etLocation);
        String desc = text(etDescription);
        String date = text(etDate);

        if (name.isEmpty()) {
            Toast.makeText(this, "Item name is required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (date.isEmpty()) {
            Toast.makeText(this, "Date is required", Toast.LENGTH_SHORT).show();
            return;
        }

        // Convert photo to base64 if exists
        String photoBase64 = "";
        if (selectedBitmap != null) {
            photoBase64 = ImageUtils.bitmapToBase64(selectedBitmap);
        }

        // LOST/FOUND::name::category::color::location::description::report_date::photo_base64
        String payload = reportType
                + "::" + name
                + "::" + category
                + "::" + color
                + "::" + location
                + "::" + desc
                + "::" + date
                + "::" + photoBase64;

        Intent data = new Intent();
        data.putExtra("REPORT_MESSAGE", payload);
        setResult(RESULT_OK, data);
        finish();
    }

    private String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }
}
