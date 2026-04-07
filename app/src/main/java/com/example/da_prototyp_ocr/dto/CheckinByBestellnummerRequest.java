package com.example.da_prototyp_ocr.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Request-Body für Check-In per Bestellnummer.
 * Wird an POST /api/anwesenheiten/.../checkin/bestellnummer geschickt.
 */
public class CheckinByBestellnummerRequest {

    @SerializedName("bestellnummer")
    private String bestellnummer;  // z.B. "#123456"

    @SerializedName("anzahl_eingecheckt")
    private int anzahlEingecheckt;  // Wie viele Personen einchecken (bei Gruppenreservierung)

    public CheckinByBestellnummerRequest(String bestellnummer, int anzahlEingecheckt) {
        this.bestellnummer = bestellnummer;
        this.anzahlEingecheckt = anzahlEingecheckt;
    }
}