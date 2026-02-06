package com.example.da_prototyp_ocr.dto;

import com.example.da_prototyp_ocr.model.Anwesenheit;
import com.google.gson.annotations.SerializedName;

public class CheckinByNameResponse {

    @SerializedName("hinweis")
    private String hinweis;

    @SerializedName("result")
    private Anwesenheit result;

    public String getHinweis() { return hinweis; }
    public Anwesenheit getResult() { return result; }
}
