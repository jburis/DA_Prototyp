package com.example.da_prototyp_ocr.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.da_prototyp_ocr.R;

/**
 * Einfacher Login-Screen für den Admin-Bereich.
 * Credentials sind aktuell hardcoded – bei Bedarf durch API-Auth ersetzen.
 */
public class AdminLoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        EditText etUser = findViewById(R.id.et_admin);
        EditText etPass = findViewById(R.id.et_password);
        Button btnLogin = findViewById(R.id.btn_login);

        btnLogin.setOnClickListener(v -> {
            String user = etUser.getText().toString().trim();
            String pass = etPass.getText().toString().trim();

            // Hardcoded Credentials (für Demo/Prototyp ausreichend)
            if (user.equals("Admin") && pass.equals("admin")) {
                Toast.makeText(this, "Login erfolgreich", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, AdminAreaActivity.class));
                finish();  // Login-Screen schließen, damit Back-Button nicht hierher zurückführt
            } else {
                Toast.makeText(this, "Falsche Daten", Toast.LENGTH_SHORT).show();
            }
        });
    }
}