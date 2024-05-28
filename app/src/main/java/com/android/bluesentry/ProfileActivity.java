package com.android.bluesentry;

import static android.content.ContentValues.TAG;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.bluesentry.databinding.ActivityProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private DatabaseReference databaseReference;
    private static final int PICK_IMAGE_REQUEST = 1;
    private Uri filePath;
    private FirebaseUser currentUser;
    private FirebaseStorage storage;
    private StorageReference storageReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize Firebase Database and Auth
        databaseReference = FirebaseDatabase.getInstance().getReference();
        currentUser = FirebaseAuth.getInstance().getCurrentUser();

        // Initialize Firebase Storage
        storage = FirebaseStorage.getInstance();
        storageReference = storage.getReference();

        disableEditing();

        if (currentUser != null) {
            retrieveUserData(currentUser.getUid());
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
        }

        binding.uploadButton.setOnClickListener(v -> openFileChooser());

        binding.editButton.setOnClickListener(v -> enableEditing());

        binding.saveButton.setOnClickListener(v -> saveUserData());

        binding.backButton.setOnClickListener(v -> {
            Intent intent = new Intent(ProfileActivity.this, HomeActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void disableEditing() {
        binding.usernameInput.setEnabled(false);
        binding.CityInput.setEnabled(false);
        binding.countryInput.setEnabled(false);
        binding.emailInput.setEnabled(false);
        binding.saveButton.setEnabled(false);
        binding.saveButton.setVisibility(View.GONE);
        binding.editButton.setVisibility(View.VISIBLE);
        binding.uploadButton.setEnabled(false);
    }

    private void enableEditing() {
        binding.usernameInput.setEnabled(true);
        binding.CityInput.setEnabled(true);
        binding.countryInput.setEnabled(true);
        binding.emailInput.setEnabled(true);
        binding.saveButton.setEnabled(true);
        binding.uploadButton.setEnabled(true);
        binding.saveButton.setVisibility(View.VISIBLE);
        binding.editButton.setVisibility(View.GONE);
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            filePath = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), filePath);
                binding.profileImage.setImageBitmap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveUserData() {
        if (currentUser != null) {
            String userId = currentUser.getUid();
            String username = binding.usernameInput.getText().toString().trim();
            String city = binding.CityInput.getText().toString().trim();
            String country = binding.countryInput.getText().toString().trim();
            String email = binding.emailInput.getText().toString().trim();

            if (username.isEmpty() || city.isEmpty() || country.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            DatabaseReference userRef = databaseReference.child("users").child(userId);
            userRef.child("username").setValue(username);
            userRef.child("city").setValue(city);
            userRef.child("country").setValue(country);
            userRef.child("email").setValue(email);

            if (filePath != null) {
                uploadImage(userId);
            } else {
                disableEditing();
                Toast.makeText(this, "User data saved successfully", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void retrieveUserData(String userId) {
        DatabaseReference userRef = databaseReference.child("users").child(userId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String username = dataSnapshot.child("username").getValue(String.class);
                    String city = dataSnapshot.child("city").getValue(String.class);
                    String country = dataSnapshot.child("country").getValue(String.class);
                    String email = dataSnapshot.child("email").getValue(String.class);
                    String profilePicUrl = dataSnapshot.child("profilePicUrl").getValue(String.class);

                    binding.usernameInput.setText(username);
                    binding.CityInput.setText(city);
                    binding.countryInput.setText(country);
                    binding.emailInput.setText(email);

                    if (profilePicUrl != null && !profilePicUrl.isEmpty()) {
                        Picasso.get().load(profilePicUrl).into(binding.profileImage);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("Firebase", "Error retrieving user data: " + databaseError.getMessage());
            }
        });
    }

    private void uploadImage(String userId) {
        if (filePath != null) {
            ProgressDialog progressDialog = new ProgressDialog(this);
            progressDialog.setTitle("Uploading...");
            progressDialog.show();

            StorageReference ref = storageReference.child(UUID.randomUUID().toString());

            ref.putFile(filePath)
                    .addOnSuccessListener(taskSnapshot -> {
                        ref.getDownloadUrl().addOnSuccessListener(uri -> {
                            String imageUrl = uri.toString();
                            databaseReference.child("users").child(userId).child("profilePicUrl").setValue(imageUrl)
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful()) {
                                            // Update profile picture URL in Firestore
                                            FirebaseFirestore db = FirebaseFirestore.getInstance();
                                            Map<String, Object> profilePic = new HashMap<> ();
                                            profilePic.put("ProfilePic", imageUrl);

                                            db.collection("users").document("PROFILE_PIC")
                                                    .set(profilePic)
                                                    .addOnSuccessListener(aVoid -> {
                                                        Log.d(TAG, "DocumentSnapshot successfully written!");
                                                    })
                                                    .addOnFailureListener(e -> {
                                                        Log.w(TAG, "Error writing document", e);
                                                    });

                                            progressDialog.dismiss();
                                            Toast.makeText(ProfileActivity.this, "Image Uploaded!!", Toast.LENGTH_SHORT).show();
                                            disableEditing();
                                        } else {
                                            progressDialog.dismiss();
                                            Toast.makeText(ProfileActivity.this, "Failed to update profile picture URL", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                        });
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        Toast.makeText(ProfileActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    })
                    .addOnProgressListener(taskSnapshot -> {
                        double progress = (100.0 * taskSnapshot.getBytesTransferred() / taskSnapshot.getTotalByteCount());
                        progressDialog.setMessage("Uploaded " + (int) progress + "%");
                    });
        }
    }

}
