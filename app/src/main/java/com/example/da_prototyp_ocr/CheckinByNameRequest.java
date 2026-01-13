package com.example.da_prototyp_ocr;

import com.google.gson.annotations.SerializedName;

public class CheckinByNameRequest {

    @SerializedName("vorname")
    private String vorname;

    @SerializedName("nachname")
    private String nachname;

    @SerializedName("anzahl_eingecheckt")
    private int anzahlEingecheckt;

    public CheckinByNameRequest(String vorname, String nachname, int anzahlEingecheckt) {
        this.vorname = vorname;
        this.nachname = nachname;
        this.anzahlEingecheckt = anzahlEingecheckt;
    }
}
