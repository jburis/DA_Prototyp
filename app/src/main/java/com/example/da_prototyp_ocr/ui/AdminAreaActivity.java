package com.example.da_prototyp_ocr.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.da_prototyp_ocr.R;

import java.util.ArrayList;

public class    AdminAreaActivity extends AppCompatActivity {
    private ArrayAdapter<String> adapter;
    private ArrayList<String> teilnehmerListe;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_area);

        teilnehmerListe = new ArrayList<>();
        teilnehmerListe.add("Teilnehmer1");
        teilnehmerListe.add("Teilnehmer2");
        teilnehmerListe.add("Teilnehmer3");

        ListView listView = findViewById(R.id.lv_teilnehmer);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, teilnehmerListe);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String entfernt = teilnehmerListe.remove(position);
            adapter.notifyDataSetChanged();
            Toast.makeText(this, entfernt + " entfernt", Toast.LENGTH_SHORT).show();
        });

        EditText etNeuerTeilnehmer = findViewById(R.id.et_neuer_teilnehmer);
        Button btnAdd = findViewById(R.id.btn_add);
        btnAdd.setOnClickListener(v -> {
            String name = etNeuerTeilnehmer.getText().toString().trim();
            if (!name.isEmpty()) {
                teilnehmerListe.add(name);
                adapter.notifyDataSetChanged();
                etNeuerTeilnehmer.setText(""); // Feld leeren
            } else {
                Toast.makeText(this, "Bitte Namen eingeben!", Toast.LENGTH_SHORT).show();
            }
        });

        Button btnLogout = findViewById(R.id.btn_logout);
        btnLogout.setOnClickListener(v -> {
            finish(); // oder zu Login zurück
        });
    }
}
