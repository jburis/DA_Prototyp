package com.example.da_prototyp_ocr.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.da_prototyp_ocr.R;
import com.google.android.material.bottomnavigation.BottomNavigationView; // Import für BottomNavigationView

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StartActivity extends AppCompatActivity {

    private TextView timeText;
    private TextView dateText;
    private final Handler handler = new Handler();
// Denis war hier
    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeAndDate();
            handler.postDelayed(this, 1000); // Aktualisiert jede Sekunde
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        timeText = findViewById(R.id.timeText);
        dateText = findViewById(R.id.dateText);

        // Starte den Timer für Uhrzeit und Datum
        handler.post(updateTimeRunnable);

        // Button zum Öffnen der Listen-Auswahl
        Button btnList = findViewById(R.id.btn_list);
        btnList.setOnClickListener(v -> {
            Intent intent = new Intent(StartActivity.this, SelectListActivity.class);
            startActivity(intent);
        });

        // BottomNavigationView Integration für Admin-Login
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_admin) {
                Intent intent = new Intent(StartActivity.this, AdminLoginActivity.class);
                startActivity(intent);
                return true;
            }
            // Optional: weitere Tabs (Home, Personen, ...)
            return false;
        });
    }

    private void updateTimeAndDate() {
        Date now = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.GERMANY);
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.GERMANY);

        dateText.setText(dateFormat.format(now));
        timeText.setText(timeFormat.format(now));
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(updateTimeRunnable);
        super.onDestroy();
    }
}
