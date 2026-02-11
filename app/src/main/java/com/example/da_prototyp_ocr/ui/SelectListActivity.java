// SelectListActivity.java
package com.example.da_prototyp_ocr.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.da_prototyp_ocr.R;
import com.example.da_prototyp_ocr.model.Veranstaltung;
import com.example.da_prototyp_ocr.network.ApiClient;
import com.example.da_prototyp_ocr.network.ApiService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

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
    private final List<String> titles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_list);

        listView = findViewById(R.id.listView);
        btnImportPdf = findViewById(R.id.btn_import_pdf);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, titles);
        listView.setAdapter(adapter);

        setupBottomNav();

        loadVeranstaltungen();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Veranstaltung v = veranstaltungen.get(position);
            Intent intent = new Intent(SelectListActivity.this, AttendanceCheckInActivity.class);
            intent.putExtra("VERANSTALTUNG_ID", v.getVeranstaltungId());
            startActivity(intent);
        });

        btnImportPdf.setOnClickListener(v -> {
            Intent intent = new Intent(SelectListActivity.this, AttendanceSheetImportActivity.class);
            startActivityForResult(intent, REQ_IMPORT_PDF);
        });
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);



        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                startActivity(new Intent(SelectListActivity.this, StartActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }



            if (id == R.id.nav_admin) {
                startActivity(new Intent(SelectListActivity.this, AdminLoginActivity.class));
                overridePendingTransition(0, 0);
                return true;
            }

            return false;
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_IMPORT_PDF && resultCode == RESULT_OK) {
            loadVeranstaltungen();
        }
    }

    private void loadVeranstaltungen() {
        ApiService api = ApiClient.getClient().create(ApiService.class);

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
                    Toast.makeText(SelectListActivity.this,
                            "Fehler beim Laden der Listen: " + response.code(),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<List<Veranstaltung>> call, Throwable t) {
                Log.e(TAG, "Network failure", t);
                Toast.makeText(SelectListActivity.this,
                        "Netzwerkfehler: " + t.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}