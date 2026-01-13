package com.example.da_prototyp_ocr;

import com.google.gson.annotations.SerializedName;

public class Anwesenheit {

    @SerializedName("anwesenheit_id")
    private int anwesenheitId;

    @SerializedName("buchung_id")
    private int buchungId;

    @SerializedName("anzahl_eingecheckt")
    private int anzahlEingecheckt;

    @SerializedName("checkin_zeit")
    private String checkinZeit;

    public int getAnwesenheitId() { return anwesenheitId; }
    public int getBuchungId() { return buchungId; }
    public int getAnzahlEingecheckt() { return anzahlEingecheckt; }
    public String getCheckinZeit() { return checkinZeit; }
}
