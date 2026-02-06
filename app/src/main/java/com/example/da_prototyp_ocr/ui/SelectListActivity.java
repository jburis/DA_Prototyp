package com.example.da_prototyp_ocr.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.da_prototyp_ocr.R;
import com.example.da_prototyp_ocr.model.Veranstaltung;
import com.example.da_prototyp_ocr.network.ApiClient;
import com.example.da_prototyp_ocr.network.ApiService;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SelectListActivity extends AppCompatActivity {

    private static final int REQ_IMPORT_PDF = 2001;
    private static final String TAG = "SelectListActivity";

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

        // 2) Klick auf Veranstaltung -> AttendanceCheckInActivity öffnen
        listView.setOnItemClickListener((parent, view, position, id) -> {
            Veranstaltung v = veranstaltungen.get(position);

            Intent intent = new Intent(SelectListActivity.this, AttendanceCheckInActivity.class);
            intent.putExtra("VERANSTALTUNG_ID", v.getVeranstaltungId());
            startActivity(intent);
        });

        // 3) PDF Import öffnen
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

        // ✅ HIER: echte URL loggen
        Call<List<Veranstaltung>> call = api.getVeranstaltungen();
        Log.d(TAG, "Request URL: " + call.request().url());

        call.enqueue(new Callback<List<Veranstaltung>>() {
            @Override
            public void onResponse(Call<List<Veranstaltung>> call, Response<List<Veranstaltung>> response) {
                Log.d(TAG, "Response code: " + response.code());

                if (response.isSuccessful() && response.body() != null) {
                    veranstaltungen.clear();
                    titles.clear();

                    veranstaltungen.addAll(response.body());
                    for (Veranstaltung v : veranstaltungen) {
                        titles.add(v.getVeranstaltungName());
                    }

                    adapter.notifyDataSetChanged();
                } else {
                    String msg = "Fehler beim Laden der Listen: " + response.code();
                    Toast.makeText(SelectListActivity.this, msg, Toast.LENGTH_LONG).show();

                    // ✅ optional: errorBody loggen (hilft oft extrem)
                    try {
                        if (response.errorBody() != null) {
                            Log.e(TAG, "ErrorBody: " + response.errorBody().string());
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "ErrorBody read failed", e);
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Veranstaltung>> call, Throwable t) {
                Log.e(TAG, "Network failure", t);
                Toast.makeText(SelectListActivity.this, "Netzwerkfehler: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}