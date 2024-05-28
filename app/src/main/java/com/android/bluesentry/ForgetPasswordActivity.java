package com.android.bluesentry;

import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.bluesentry.databinding.ActivityForgetPasswordBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ForgetPasswordActivity extends AppCompatActivity {

    ActivityForgetPasswordBinding binding;
    FirebaseAuth mAuth;
    DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        binding = ActivityForgetPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        databaseReference = FirebaseDatabase.getInstance().getReference();

        binding.backButton.setOnClickListener(v -> {
            onBackPressed();
        });

        binding.sendBtn.setOnClickListener(v -> {
            String email = binding.emailInput.getText().toString();
            binding.progressBar.setVisibility(android.view.View.VISIBLE);

            if (email.isEmpty()) {
                binding.emailInput.setError("Enter Email");
                binding.progressBar.setVisibility(android.view.View.GONE);
            } else {
                checkEmailAndSendResetLink(email);
            }
        });
    }

    private void checkEmailAndSendResetLink(String email) {
        databaseReference.child("users").orderByChild("email").equalTo(email).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    // Email exists in the database
                    mAuth.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(ForgetPasswordActivity.this, "Email sent", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ForgetPasswordActivity.this, "Email not sent", Toast.LENGTH_SHORT).show();
                        }
                        binding.progressBar.setVisibility(android.view.View.GONE);
                        finish();
                    });
                } else {
                    // Email does not exist in the database
                    binding.emailInput.setError("Email not found");
                    Toast.makeText(ForgetPasswordActivity.this, "Email not found", Toast.LENGTH_SHORT).show();
                    binding.progressBar.setVisibility(android.view.View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(ForgetPasswordActivity.this, "Database error", Toast.LENGTH_SHORT).show();
                binding.progressBar.setVisibility(android.view.View.GONE);
            }
        });
    }
}
