package com.example.da_prototyp_ocr;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SelectListActivity extends AppCompatActivity {

    private static final int REQ_IMPORT_PDF = 2001;

    private ListView listView;
    private Button btnImportPdf;

    private ArrayAdapter<String> adapter;
    private final List<Veranstaltung> veranstaltungen = new ArrayList<>();
    private final List<String> titles = new ArrayList<>(); // Anzeige im ListView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_list);

        listView = findViewById(R.id.listView);
        btnImportPdf = findViewById(R.id.btn_import_pdf);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles);
        listView.setAdapter(adapter);

        // 1) Listen laden
        loadVeranstaltungen();

        // 2) Klick auf Veranstaltung -> MainActivity öffnen (wie bisher)
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Veranstaltung v = veranstaltungen.get(position);

            Intent intent = new Intent(SelectListActivity.this, AttendanceCheckInActivity.class);
            intent.putExtra("VERANSTALTUNG_ID", v.getVeranstaltungId());
            startActivity(intent);
        });

        // 3) NEU: PDF Import öffnen
        btnImportPdf.setOnClickListener(v -> {
            Intent intent = new Intent(SelectListActivity.this, AttendanceSheetImportActivity.class);
            startActivityForResult(intent, REQ_IMPORT_PDF);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_IMPORT_PDF && resultCode == RESULT_OK) {
            // Nach erfolgreichem Import neu laden
            loadVeranstaltungen();
        }
    }

    private void loadVeranstaltungen() {
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.getVeranstaltungen().enqueue(new Callback<List<Veranstaltung>>() {
            @Override
            public void onResponse(Call<List<Veranstaltung>> call, Response<List<Veranstaltung>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    veranstaltungen.clear();
                    titles.clear();

                    veranstaltungen.addAll(response.body());
                    for (Veranstaltung v : veranstaltungen) {
                        titles.add(v.getVeranstaltungName());
                    }

                    adapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(SelectListActivity.this, "Fehler beim Laden der Listen: " + response.code(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<List<Veranstaltung>> call, Throwable t) {
                Toast.makeText(SelectListActivity.this, "Netzwerkfehler: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
