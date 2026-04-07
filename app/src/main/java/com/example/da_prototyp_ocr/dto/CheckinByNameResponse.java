package com.example.da_prototyp_ocr.dto;

import com.example.da_prototyp_ocr.model.Anwesenheit;
import com.google.gson.annotations.SerializedName;

/**
 * API-Response für Check-In per Name.
 * Enthält die erstellte Anwesenheit und optional einen Hinweis vom Server.
 */
public class CheckinByNameResponse {

    @SerializedName("hinweis")
    private String hinweis;  // z.B. "Mehrere Personen mit diesem Namen gefunden"

    @SerializedName("result")
    private Anwesenheit result;  // Der erstellte Anwesenheits-Eintrag

    public String getHinweis() { return hinweis; }
    public Anwesenheit getResult() { return result; }
}