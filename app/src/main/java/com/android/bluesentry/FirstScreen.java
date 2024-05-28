package com.android.bluesentry;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.bluesentry.databinding.ActivityFirstScreenBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class FirstScreen extends AppCompatActivity {

    ActivityFirstScreenBinding binding;

    FirebaseAuth mAuth;

    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);
        EdgeToEdge.enable (this);
        binding = ActivityFirstScreenBinding.inflate (getLayoutInflater ());
        setContentView (binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener (findViewById (R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets (WindowInsetsCompat.Type.systemBars ());
            v.setPadding (systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance ();
        checkLoginStatus ();

        binding.loginBtn.setOnClickListener (v -> {
            startActivity (new Intent (this, LoginActivity.class));
            finish ();
        });
    }

    //method check if user is logged in or not
    private void checkLoginStatus () {
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        updateUI(currentUser);

    }

    private void updateUI (FirebaseUser currentUser) {
        if (currentUser != null) {
            startActivity (new Intent (this, HomeActivity.class));
            finish ();
        }
    }
}