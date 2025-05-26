package com.marketalchemy.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class ProfileFragment extends Fragment {
    private ShapeableImageView profileImageView;
    private TextInputEditText displayNameInput;
    private TextInputEditText bioInput;
    private SharedPreferences preferences;
    private View rootView;
    private View saveProfileButton;
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
        displayNameInput = rootView.findViewById(R.id.displayNameInput);
        bioInput = rootView.findViewById(R.id.bioInput);
        saveProfileButton = rootView.findViewById(R.id.saveProfileButton);
        logoutButton = rootView.findViewById(R.id.logoutButton);
        
        // Set default profile image
        profileImageView.setImageResource(R.drawable.default_profile);
    }

    private void loadProfileData() {
        // Always use default profile image
        profileImageView.setImageResource(R.drawable.default_profile);
        
        // Get saved display name or use default
        String displayName = preferences.getString("display_name", "");
        displayNameInput.setText(displayName);
        bioInput.setText(preferences.getString("bio", ""));
        
        // Update profile username TextView with real username
        TextView profileUsername = rootView.findViewById(R.id.profileUsername);
        if (profileUsername != null) {
            if (displayName != null && !displayName.isEmpty()) {
                profileUsername.setText(displayName);
            } else {
                profileUsername.setText("User");
            }
        }
        
        // Update profile email TextView with the real user email
        TextView profileEmail = rootView.findViewById(R.id.profileEmail);
        if (profileEmail != null) {
            // Try to get email from Firebase Auth
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null && currentUser.getEmail() != null) {
                profileEmail.setText(currentUser.getEmail());
            } else {
                // Fallback to email from preferences if Firebase user is not available
                String email = preferences.getString("email", "user@example.com");
                profileEmail.setText(email);
            }
        }
    }

    private void setupClickListeners() {
        // Save profile info
        saveProfileButton.setOnClickListener(v -> saveProfileInfo());

        // Account actions
        logoutButton.setOnClickListener(v -> {
            // Implement logout
            logout();
        });
    }

    private void saveProfileInfo() {
        String displayName = displayNameInput.getText().toString();
        String bio = bioInput.getText().toString();

        preferences.edit()
                .putString("display_name", displayName)
                .putString("bio", bio)
                .apply();
        
        // Update profile username TextView with the new display name
        TextView profileUsername = rootView.findViewById(R.id.profileUsername);
        if (profileUsername != null) {
            if (displayName != null && !displayName.isEmpty()) {
                profileUsername.setText(displayName);
            } else {
                profileUsername.setText("User");
            }
        }
        
        // Make sure email is still shown (in case it was updated elsewhere)
        TextView profileEmail = rootView.findViewById(R.id.profileEmail);
        if (profileEmail != null) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null && currentUser.getEmail() != null) {
                profileEmail.setText(currentUser.getEmail());
            }
        }

        Toast.makeText(getContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Logs the user out by clearing user data and navigating to the SignupActivity
     */
    private void logout() {
        // Show confirmation dialog
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Confirm Logout")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Yes", (dialog, which) -> {
                try {
                    // Clear user preferences/data
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.clear();
                    editor.apply();
                    
                    // Sign out from Firebase Auth if it's being used
                    if (com.google.firebase.auth.FirebaseAuth.getInstance() != null) {
                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                    }
                    
                    // Show logout message
                    Toast.makeText(getContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();
                    
                    // Navigate to SignupActivity
                    Intent intent = new Intent(getActivity(), SignupActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    
                    // Finish current activity
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                } catch (Exception e) {
                    // Log and display any errors during logout
                    android.util.Log.e("ProfileFragment", "Error during logout: " + e.getMessage());
                    Toast.makeText(getContext(), "Error during logout: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("No", null)
            .show();
    }
} 