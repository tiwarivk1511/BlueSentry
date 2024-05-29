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
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.bluesentry.databinding.ActivityProfileBinding;
import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.squareup.picasso.Picasso;

import java.io.IOException;

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
        ViewCompat.setOnApplyWindowInsetsListener (binding.main, (v, insets) -> {
            Insets systemBars = insets.getInsets (WindowInsetsCompat.Type.systemBars ());
            v.setPadding (systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


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

        binding.uploadButton.setOnClickListener(v -> SelectImage());

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

            try{
                if (filePath != null) {
                    // Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), filePath);
                    uploadImage(userId);

                } else {
                    disableEditing();
                    Toast.makeText(this, "User data saved successfully", Toast.LENGTH_SHORT).show();
                }
            }
            catch (Exception e){
                e.printStackTrace();
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

                    binding.usernameInput.setText(username);
                    binding.CityInput.setText(city);
                    binding.countryInput.setText(country);
                    binding.emailInput.setText(email);

                    fetchImage (userId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("Firebase", "Error retrieving user data: " + databaseError.getMessage());
            }
        });
    }

    // Select Image method
    private void SelectImage()
    {

        // Defining Implicit Intent to mobile gallery
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(
                Intent.createChooser(
                        intent,
                        "Select Image from here..."),
                PICK_IMAGE_REQUEST);
    }

    // Override onActivityResult method
    @Override
    protected void onActivityResult(int requestCode,
                                    int resultCode,
                                    Intent data)
    {

        super.onActivityResult(requestCode,
                resultCode,
                data);

        // checking request code and result code
        // if request code is PICK_IMAGE_REQUEST and
        // resultCode is RESULT_OK
        // then set image in the image view
        if (requestCode == PICK_IMAGE_REQUEST
                && resultCode == RESULT_OK
                && data != null
                && data.getData() != null) {

            // Get the Uri of data
            filePath = data.getData();
            try {

                // Setting image on image view using Bitmap
                Bitmap bitmap = MediaStore
                        .Images
                        .Media
                        .getBitmap(
                                getContentResolver(),
                                filePath);
                binding.profileImage.setImageBitmap(bitmap);
            }

            catch (IOException e) {
                // Log the exception
                e.printStackTrace();
            }
        }
    }

    // UploadImage method
    private void uploadImage(String userId)
    {
        if (filePath != null) {

            // Code for showing progressDialog while uploading
            ProgressDialog progressDialog
                    = new ProgressDialog(this);
            progressDialog.setTitle("Uploading...");
            progressDialog.show();

            // Defining the child of storageReference
            StorageReference ref
                    = storageReference
                    .child(
                            userId + "/images/"
                                    + userId);

            // adding listeners on upload
            // or failure of image
            ref.putFile(filePath)
                    .addOnSuccessListener(
                            new OnSuccessListener<UploadTask.TaskSnapshot> () {

                                @Override
                                public void onSuccess(
                                        UploadTask.TaskSnapshot taskSnapshot)
                                {
                                    // Image uploaded successfully
                                    // Dismiss dialog
                                    progressDialog.dismiss();
                                    Toast
                                            .makeText(ProfileActivity.this,
                                                    "Image Uploaded!!",
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                }
                            })

                    .addOnFailureListener(new OnFailureListener () {
                        @Override
                        public void onFailure(@NonNull Exception e)
                        {

                            // Error, Image not uploaded
                            progressDialog.dismiss();
                            Toast
                                    .makeText(ProfileActivity.this,
                                            "Failed " + e.getMessage(),
                                            Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }).addOnProgressListener (new OnProgressListener<UploadTask.TaskSnapshot> () {
                        @Override
                        public void onProgress (@NonNull UploadTask.TaskSnapshot snapshot) {

                            double progress
                                    = (100.0
                                    * snapshot.getBytesTransferred()
                                    / snapshot.getTotalByteCount());
                            progressDialog.setMessage
                                    ("Uploaded "
                                            + (int)progress + "%");
                        }
                    });

        }
    }

    private void fetchImage(String userId) {
        // Get a reference to the location where the image was uploaded
        StorageReference imageRef = storageReference.child(userId + "/images/" +  userId); // Replace imageName with the actual name of the image

        // Fetch the download URL for the image
        imageRef.getDownloadUrl().addOnSuccessListener(uri -> {
            // Load the image using the download URL
            String imageUrl = uri.toString();

            // Now you can use this imageUrl to load the image into an ImageView or perform any other actions
            // For example, you can use Picasso or Glide to load the image into an ImageView
             Picasso.get().load(imageUrl).error (R.drawable.error_placeholder).into(binding.profileImage);
            // Glide.with(this).load(imageUrl).into(imageView);

            // Here, you can also store the imageUrl in a database or perform any other necessary actions
        }).addOnFailureListener(exception -> {
            // Handle any errors
            Log.e(TAG, "Failed to fetch image: " + exception.getMessage());
            Toast.makeText(ProfileActivity.this, "Failed to fetch image", Toast.LENGTH_SHORT).show();
        });
    }


    private void loadImageFromUrl(String imageUrl, ImageView imageView) {
        Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.profile_picture_placeholder)  // placeholder image
                .error(R.drawable.error_placeholder)              // error image
                .into(imageView);
    }

}
