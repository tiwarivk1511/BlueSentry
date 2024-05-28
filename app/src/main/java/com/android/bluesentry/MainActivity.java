package com.android.bluesentry;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.FirebaseApp;

public class MainActivity extends AppCompatActivity {

    FirebaseApp firebaseApp;
    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);
        EdgeToEdge.enable (this);
        setContentView (R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener (findViewById (R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets (WindowInsetsCompat.Type.systemBars ());
            v.setPadding (systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // initialize firebase
        firebaseApp = FirebaseApp.initializeApp (this);

        // start the Home activity after 3 seconds
        new android.os.Handler ().postDelayed (() -> {
            startActivity (new Intent (this, FirstScreen.class));
            finish ();
        }, 3000);
    }
}