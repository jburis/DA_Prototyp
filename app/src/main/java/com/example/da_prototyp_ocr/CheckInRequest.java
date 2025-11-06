package com.example.da_prototyp_ocr; // Passen Sie das Paket an Ihr Projekt an

import com.google.gson.annotations.SerializedName;

public class CheckInRequest {

    // Der Body erwartet ein optionales "count"-Feld
    @SerializedName("count")
    private int count;

    public CheckInRequest(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }
}
