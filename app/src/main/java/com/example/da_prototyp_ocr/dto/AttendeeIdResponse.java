package com.example.da_prototyp_ocr.dto;

import com.google.gson.annotations.SerializedName;

/**
 * API-Response wenn eine neue Anwesenheit erstellt wurde.
 * Enthält nur die ID des neu erstellten Eintrags.
 */
public class AttendeeIdResponse {

    @SerializedName("id")  // So heißt das Feld im JSON der API
    private Integer id;

    public Integer getId() {
        return id;
    }
}