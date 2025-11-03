package com.example.da_prototyp_ocr;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AdminLoginActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        EditText etUser = findViewById(R.id.et_user);
        EditText etPass = findViewById(R.id.et_pass);
        Button btnLogin = findViewById(R.id.btn_login);

        btnLogin.setOnClickListener(v -> {
            String user = etUser.getText().toString();
            String pass = etPass.getText().toString();
            if (user.equals("Admin") && pass.equals("admin")) {
                Toast.makeText(this, "Login erfolgreich!", Toast.LENGTH_SHORT).show();
                // TODO: Admin-Dashboard öffnen
            } else {
                Toast.makeText(this, "Falsche Daten!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
