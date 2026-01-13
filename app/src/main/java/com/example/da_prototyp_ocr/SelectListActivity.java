package com.example.da_prototyp_ocr;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SelectListActivity extends AppCompatActivity {

    private ListView listView;
    private ArrayAdapter<Veranstaltung> adapter;
    private List<Veranstaltung> veranstaltungen = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_list);

        listView = findViewById(R.id.listView);

        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                veranstaltungen
        );
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Veranstaltung v = veranstaltungen.get(position);

            Intent intent = new Intent(
                    SelectListActivity.this,
                    MainActivity.class
            );
            intent.putExtra("VERANSTALTUNG_ID", v.getVeranstaltungId());
            startActivity(intent);
        });

        loadVeranstaltungen();
    }

    private void loadVeranstaltungen() {
        ApiService api = ApiClient.getClient().create(ApiService.class);

        api.getVeranstaltungen().enqueue(new Callback<List<Veranstaltung>>() {
            @Override
            public void onResponse(
                    @NonNull Call<List<Veranstaltung>> call,
                    @NonNull Response<List<Veranstaltung>> response
            ) {
                if (response.isSuccessful() && response.body() != null) {
                    veranstaltungen.clear();
                    veranstaltungen.addAll(response.body());
                    adapter.notifyDataSetChanged();
                } else {
                    Toast.makeText(
                            SelectListActivity.this,
                            "Fehler beim Laden der Veranstaltungen",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }

            @Override
            public void onFailure(
                    @NonNull Call<List<Veranstaltung>> call,
                    @NonNull Throwable t
            ) {
                Toast.makeText(
                        SelectListActivity.this,
                        "API nicht erreichbar",
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }
}
