package com.example.da_prototyp_ocr; // Passen Sie das Paket an Ihr Projekt an

import com.google.gson.annotations.SerializedName;

public class AttendeeIdResponse {

    @SerializedName("id")
    private Integer id;

    public Integer getId() {
        return id;
    }
}
