package com.example.da_prototyp_ocr.network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton für die Retrofit-Instanz.
 * Alle API-Calls laufen über diesen Client.
 */
public class ApiClient {

    // Backend läuft auf dem TGM-Server (wird später zu KWP migriert)
    private static final String BASE_URL = "https://projekte.tgm.ac.at/keci/";

    private static Retrofit retrofit = null;

    /**
     * Gibt die Retrofit-Instanz zurück (erstellt sie beim ersten Aufruf).
     * Verwendung: ApiClient.getClient().create(ApiService.class)
     */
    public static Retrofit getClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())  // JSON ↔ Java automatisch
                    .build();
        }
        return retrofit;
    }
}