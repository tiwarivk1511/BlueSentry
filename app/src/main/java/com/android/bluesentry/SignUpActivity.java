package com.android.bluesentry;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.bluesentry.databinding.ActivitySignUpBinding;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.squareup.picasso.Picasso;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {
    private static final int RC_SIGN_IN = 9001;
    private static final String TAG = "SignupActivity";
    private Uri imageUri;
    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;
    private ActivitySignUpBinding binding;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        dbRef = FirebaseDatabase.getInstance().getReference();

        binding.SignupButton.setOnClickListener(v -> performEmailSignup());

        binding.uploadImage.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(intent, 1);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            imageUri = data.getData();
            binding.profileImage.setImageURI(imageUri);
        }
    }

    private void performEmailSignup() {
        String username = binding.usernameInput.getText().toString().trim();
        String city = binding.CityInput.getText().toString().trim();
        String country = binding.countryInput.getText().toString().trim();
        String email = binding.emailInput.getText().toString().trim();
        String password = binding.passwordInput.getText().toString().trim();

        if (username.isEmpty() || city.isEmpty() || country.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserToRealtimeDatabase(user, username, city, country);
                        }
                    } else {
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        Toast.makeText(SignUpActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserToRealtimeDatabase(FirebaseUser user, String username, String city, String country) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("uid", user.getUid());
        userMap.put("username", username);
        userMap.put("city", city);
        userMap.put("country", country);
        userMap.put("email", user.getEmail());

        // Convert the Uri to a String representing the URL
        String imageUrl = imageUri != null ? imageUri.toString() : "";

        // Use a different key for the profile picture URL
        userMap.put("profilePictureUrl", imageUrl);

        dbRef.child("users").child(user.getUid()).setValue(userMap)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(SignUpActivity.this, "User data saved", Toast.LENGTH_SHORT).show();
                        if (imageUri != null) {
                            uploadImage(user.getUid(), imageUri);
                        } else {
                            navigateToHome();
                        }
                    } else {
                        Toast.makeText(SignUpActivity.this, "Failed to save user data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void uploadImage(String userId, Uri imageUri) {
        if (imageUri == null) {
            Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
            return;
        }
        else{
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            Map<String, Object> ProfilePic = new HashMap<>();
            ProfilePic.put("ProfilePic", imageUri);


            db.collection("users").document("PROFILE_PIC")
                    .set(ProfilePic)
                    .addOnSuccessListener(new OnSuccessListener<Void> () {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d(TAG, "DocumentSnapshot successfully written!");
                        }
                    })
                    .addOnFailureListener(new OnFailureListener () {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.w(TAG, "Error writing document", e);
                        }
                    });

//            StorageReference storageRef = FirebaseStorage.getInstance().getReference();
//            StorageReference userImageRef = storageRef.child("users/" + userId + "/profilePic.jpg");
//
//            userImageRef.putFile(imageUri)
//                    .addOnSuccessListener(taskSnapshot -> {
//                        userImageRef.getDownloadUrl().addOnSuccessListener(uri -> {
//                            String imageUrl = uri.toString();
//                            saveImageUrlToDatabase(userId, imageUrl);
//                        }).addOnFailureListener(e -> {
//                            Log.e("Firebase", "Error retrieving image URL: " + e.getMessage());
//                            Toast.makeText(this, "Error retrieving image URL", Toast.LENGTH_SHORT).show();
//                        });
//                    }).addOnFailureListener(e -> {
//                        Log.e("Firebase", "Failed to upload image: " + e.getMessage());
//                        Log.e("Firebase", "Image URI: " + imageUri.toString());
//                        Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show();
//                    });

        }

    }

    private void saveImageUrlToDatabase(String userId, String imageUrl) {
        DatabaseReference databaseRef = FirebaseDatabase.getInstance().getReference();
        databaseRef.child("users").child(userId).child("profilePicUrl").setValue(imageUrl)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Image uploaded successfully", Toast.LENGTH_SHORT).show();
                        navigateToHome();
                    }
                }).addOnFailureListener(e -> {
                    Log.e("Firebase", "Error saving image URL to database: " + e.getMessage());
                    Toast.makeText(this, "Failed to save image URL", Toast.LENGTH_SHORT).show();
                });
    }

    private void navigateToHome() {
        Intent intent = new Intent(SignUpActivity.this, HomeActivity.class);
        startActivity(intent);
        finish();
    }

    public void getImageUrl() {
        FirebaseStorage storage = FirebaseStorage.getInstance();
        StorageReference storageRef = storage.getReference();
        StorageReference profilePicRef = storageRef.child("users/" + FirebaseAuth.getInstance().getCurrentUser().getUid() + "/profilePic.jpg");

        profilePicRef.getDownloadUrl().addOnSuccessListener(uri -> {
            String imageUrl = uri.toString();
            Picasso.get().load(imageUrl).into(binding.profileImage);
        }).addOnFailureListener(e -> {
            Log.e("Firebase", "Error retrieving image URL: " + e.getMessage());
        });
    }
}
