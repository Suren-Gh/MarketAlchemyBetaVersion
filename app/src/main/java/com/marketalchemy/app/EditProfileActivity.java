package com.marketalchemy.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.marketalchemy.app.utils.ThemeManager;

public class EditProfileActivity extends AppCompatActivity {

    private ImageView ivBack;
    private Button btnSave;
    private EditText etFullName, etUsername;
    private FirebaseAuth mAuth;
    private String originalFullName = "";
    private String originalUsername = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.initDarkMode();
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        mAuth = FirebaseAuth.getInstance();

        ivBack = findViewById(R.id.ivBack);
        btnSave = findViewById(R.id.btnSave);
        etFullName = findViewById(R.id.etFullName);
        etUsername = findViewById(R.id.etUsername);

        ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveProfileChanges();
            }
        });

        loadUserData();
    }

    private void loadUserData() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            String displayName = user.getDisplayName();
            
            // Parse display name to extract full name and username
            if (displayName != null && !displayName.isEmpty()) {
                if (displayName.contains("(") && displayName.contains(")")) {
                    originalFullName = displayName.substring(0, displayName.indexOf("(")).trim();
                    originalUsername = displayName.substring(
                            displayName.indexOf("(") + 1,
                            displayName.indexOf(")")
                    );
                    
                    etFullName.setText(originalFullName);
                    etUsername.setText(originalUsername);
                } else {
                    etFullName.setText(displayName);
                }
            }
        }
    }

    private void saveProfileChanges() {
        String fullName = etFullName.getText().toString().trim();
        String username = etUsername.getText().toString().trim();

        // Validate inputs
        if (TextUtils.isEmpty(fullName)) {
            etFullName.setError("Full name is required");
            etFullName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(username)) {
            etUsername.setError("Username is required");
            etUsername.requestFocus();
            return;
        }

        if (username.contains(" ")) {
            etUsername.setError("Username cannot contain spaces");
            etUsername.requestFocus();
            return;
        }

        // Check if anything changed
        if (fullName.equals(originalFullName) && username.equals(originalUsername)) {
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Show loading state
        btnSave.setEnabled(false);
        btnSave.setText("Saving...");

        // Update profile
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Format the display name to include both full name and username
            String newDisplayName = fullName + " (" + username + ")";
            
            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                    .setDisplayName(newDisplayName)
                    .build();

            user.updateProfile(profileUpdates)
                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                        @Override
                        public void onComplete(@NonNull Task<Void> task) {
                            btnSave.setEnabled(true);
                            btnSave.setText("Save");
                            
                            if (task.isSuccessful()) {
                                Toast.makeText(EditProfileActivity.this, 
                                        "Profile updated successfully", 
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(EditProfileActivity.this, 
                                        "Failed to update profile: " + task.getException().getMessage(), 
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }
} 