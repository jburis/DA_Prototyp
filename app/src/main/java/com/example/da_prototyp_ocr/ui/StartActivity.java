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

/**
 * Startseite der App: Zeigt Uhrzeit und Datum an.
 * Von hier aus navigiert man zur Veranstaltungsliste oder zum Admin-Bereich.
 */
public class StartActivity extends AppCompatActivity {

    private TextView timeText, dateText;
    private final Handler handler = new Handler();

    /**
     * Runnable das jede Sekunde die Uhrzeit aktualisiert.
     */
    private final Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeAndDate();
            handler.postDelayed(this, 1000);  // Alle 1000ms wiederholen
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);

        timeText = findViewById(R.id.timeText);
        dateText = findViewById(R.id.dateText);

        // Hauptbutton zur Veranstaltungsliste
        Button btnList = findViewById(R.id.btn_list);
        btnList.setOnClickListener(v ->
                startActivity(new Intent(this, SelectListActivity.class)));

        setupBottomNav();

        // Uhr starten
        handler.post(updateTimeRunnable);
    }

    /**
     * Bottom Navigation konfigurieren.
     */
    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                // Schon auf Home → nichts tun
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

    /**
     * Aktualisiert die Uhrzeit- und Datumsanzeige.
     * Wird jede Sekunde vom Handler aufgerufen.
     */
    private void updateTimeAndDate() {
        SimpleDateFormat df = new SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.GERMAN);
        SimpleDateFormat tf = new SimpleDateFormat("HH:mm:ss", Locale.GERMAN);

        Date now = new Date();
        dateText.setText(df.format(now));  // z.B. "Montag, 07.04.2026"
        timeText.setText(tf.format(now));  // z.B. "14:32:45"
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // WICHTIG: Handler stoppen, sonst Memory Leak!
        handler.removeCallbacks(updateTimeRunnable);
    }
}