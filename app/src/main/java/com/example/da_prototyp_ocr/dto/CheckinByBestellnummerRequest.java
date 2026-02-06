package com.example.da_prototyp_ocr.dto;

import com.google.gson.annotations.SerializedName;

public class CheckinByBestellnummerRequest {

    @SerializedName("bestellnummer")
    private String bestellnummer;

    @SerializedName("anzahl_eingecheckt")
    private int anzahlEingecheckt;

    public CheckinByBestellnummerRequest(String bestellnummer, int anzahlEingecheckt) {
        this.bestellnummer = bestellnummer;
        this.anzahlEingecheckt = anzahlEingecheckt;
    }
}
