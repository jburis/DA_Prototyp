package com.example.da_prototyp_ocr;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;

public class SelectListActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_list);

        ListView listView = findViewById(R.id.listView);
        String[] listen = {"Teilnehmerliste1", "Teilnehmerliste2", "Teilnehmerliste3"};

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, listen);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            // Öffnet beim Klick die MainActivity (Kamera)
            Intent intent = new Intent(SelectListActivity.this, MainActivity.class);
            // Optional kannst du die gewählte Liste mitgeben:
            // intent.putExtra("LIST_NAME", listen[position]);
            startActivity(intent);
        });
    }
}
