package com.example.da_prototyp_ocr.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Request-Body für Check-In per Name (QR-Code Weg).
 * Wird an POST /api/anwesenheiten/.../checkin/name geschickt.
 */
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