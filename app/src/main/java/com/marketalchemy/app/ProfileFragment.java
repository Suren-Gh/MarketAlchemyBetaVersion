package com.marketalchemy.app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.app.Activity;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class ProfileFragment extends Fragment {
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int TAKE_PHOTO_REQUEST = 2;
    private static final int PERMISSION_REQUEST_CODE = 3;
    
    private ShapeableImageView profileImageView;
    private TextInputEditText displayNameInput;
    private TextInputEditText bioInput;
    private SwitchMaterial notificationsSwitch;
    private SwitchMaterial biometricSwitch;
    private SharedPreferences preferences;
    private View rootView;
    private View changePhotoButton;
    private View saveProfileButton;
    private View changePasswordButton;
    private View logoutButton;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.fragment_profile, container, false);
        preferences = requireActivity().getSharedPreferences("ProfilePrefs", 0);
        
        initializeViews(rootView);
        loadProfileData();
        setupClickListeners();
        
        return rootView;
    }

    private void initializeViews(View rootView) {
        profileImageView = rootView.findViewById(R.id.profilePhoto);
        changePhotoButton = rootView.findViewById(R.id.changePhotoButton);
        displayNameInput = rootView.findViewById(R.id.displayNameInput);
        bioInput = rootView.findViewById(R.id.bioInput);
        saveProfileButton = rootView.findViewById(R.id.saveProfileButton);
        notificationsSwitch = rootView.findViewById(R.id.notificationsSwitch);
        biometricSwitch = rootView.findViewById(R.id.biometricSwitch);
        changePasswordButton = rootView.findViewById(R.id.changePasswordButton);
        logoutButton = rootView.findViewById(R.id.logoutButton);
    }

    private void loadProfileData() {
        String photoUri = preferences.getString("profile_photo", null);
        if (photoUri != null) {
            Glide.with(this)
                .load(Uri.parse(photoUri))
                .circleCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(profileImageView);
        }

        displayNameInput.setText(preferences.getString("display_name", ""));
        bioInput.setText(preferences.getString("bio", ""));
        notificationsSwitch.setChecked(preferences.getBoolean("notifications_enabled", true));
        biometricSwitch.setChecked(preferences.getBoolean("biometric_enabled", false));
    }

    private void setupClickListeners() {
        // Profile photo change
        profileImageView.setOnClickListener(v -> showPhotoOptionsDialog());

        // Save profile info
        saveProfileButton.setOnClickListener(v -> saveProfileInfo());

        // Other switches
        notificationsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> 
            preferences.edit().putBoolean("notifications_enabled", isChecked).apply());

        biometricSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> 
            preferences.edit().putBoolean("biometric_enabled", isChecked).apply());

        // Account actions
        changePasswordButton.setOnClickListener(v -> {
            // TODO: Implement password change
            Toast.makeText(getContext(), "Password change feature coming soon", Toast.LENGTH_SHORT).show();
        });

        logoutButton.setOnClickListener(v -> {
            // TODO: Implement logout
            Toast.makeText(getContext(), "Logout feature coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void showPhotoOptionsDialog() {
        String[] options = {"Take Photo", "Choose from Gallery", "Remove Photo"};
        
        new AlertDialog.Builder(requireContext())
            .setTitle("Change Profile Photo")
            .setItems(options, (dialog, which) -> {
                switch (which) {
                    case 0: // Take Photo
                        if (checkCameraPermission()) {
                            takePhoto();
                        }
                        break;
                    case 1: // Choose from Gallery
                        if (checkStoragePermission()) {
                            pickImage();
                        }
                        break;
                    case 2: // Remove Photo
                        removeProfilePhoto();
                        break;
                }
            })
            .show();
    }

    private boolean checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.CAMERA},
                    PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private boolean checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    private void takePhoto() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
            startActivityForResult(intent, TAKE_PHOTO_REQUEST);
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    private void removeProfilePhoto() {
        preferences.edit().remove("profile_photo").apply();
        profileImageView.setImageResource(R.drawable.default_profile);
        Toast.makeText(getContext(), "Profile photo removed", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                         @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (permissions[0].equals(Manifest.permission.CAMERA)) {
                    takePhoto();
                } else if (permissions[0].equals(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    pickImage();
                }
            } else {
                Toast.makeText(getContext(), "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri imageUri = null;
            
            if (requestCode == PICK_IMAGE_REQUEST) {
                imageUri = data.getData();
            } else if (requestCode == TAKE_PHOTO_REQUEST && data.getExtras() != null) {
                // Handle camera photo
                // Note: In a production app, you'd want to save this to a file
                // and use the file URI instead
                imageUri = Uri.parse(data.getExtras().get("data").toString());
            }
            
            if (imageUri != null) {
                // Save the URI
                preferences.edit().putString("profile_photo", imageUri.toString()).apply();
                
                // Load the image with animation
                Glide.with(this)
                    .load(imageUri)
                    .circleCrop()
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(profileImageView);
                
                Toast.makeText(getContext(), "Profile photo updated", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveProfileInfo() {
        String displayName = displayNameInput.getText().toString();
        String bio = bioInput.getText().toString();

        preferences.edit()
                .putString("display_name", displayName)
                .putString("bio", bio)
                .apply();

        Toast.makeText(getContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show();
    }
} 