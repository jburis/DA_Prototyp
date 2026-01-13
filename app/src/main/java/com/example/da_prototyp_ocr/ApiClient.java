package com.example.da_prototyp_ocr;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    // WICHTIG: Ersetzen Sie diese IP-Adresse durch die lokale IP-Adresse
    // Ihres Computers, auf dem der Node.js-Server läuft.
    // Beispiele: "http://192.168.1.10:3000/", "http://10.0.2.2:3000/" (für Android Emulator)
    private static final String BASE_URL = "http://10.0.2.2:3000/";

    private static Retrofit retrofit = null;

    /**
     * Erstellt und liefert eine Singleton-Instanz von Retrofit.
     * @return Die Retrofit-Instanz.
     */
    public static Retrofit getClient() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create()) // Verwendet Gson zur JSON-Konvertierung
                    .build();
        }
        return retrofit;
    }
}
