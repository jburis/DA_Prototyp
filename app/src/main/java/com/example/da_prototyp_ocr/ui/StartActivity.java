package com.example.da_prototyp_ocr.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.da_prototyp_ocr.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StartActivity extends AppCompatActivity {

    private TextView timeText, dateText;
    private final Handler handler = new Handler();

    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeAndDate();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        timeText = findViewById(R.id.timeText);
        dateText = findViewById(R.id.dateText);

        Button btnList = findViewById(R.id.btn_list);
        btnList.setOnClickListener(v ->
                startActivity(new Intent(this, SelectListActivity.class)));

        setupBottomNav();

        handler.post(updateTimeRunnable);
    }

    private void setupBottomNav() {

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        bottomNav.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            if (id == R.id.nav_home) {
                return true;
            }

            if (id == R.id.nav_list) {
                startActivity(new Intent(this, SelectListActivity.class));
                return true;
            }

            if (id == R.id.nav_admin) {
                startActivity(new Intent(this, AdminLoginActivity.class));
                return true;
            }

            return false;
        });
    }

    private void updateTimeAndDate() {
        SimpleDateFormat df = new SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.GERMAN);
        SimpleDateFormat tf = new SimpleDateFormat("HH:mm:ss", Locale.GERMAN);

        Date now = new Date();
        dateText.setText(df.format(now));
        timeText.setText(tf.format(now));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateTimeRunnable);
    }
}