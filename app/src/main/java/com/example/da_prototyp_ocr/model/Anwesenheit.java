package com.example.da_prototyp_ocr.model;

import com.google.gson.annotations.SerializedName;

/**
 * Repräsentiert einen Check-In Eintrag in der Datenbank.
 * Jede Anwesenheit gehört zu einer Buchung und speichert wann/wie viele eingecheckt wurden.
 */
public class Anwesenheit {

    @SerializedName("anwesenheit_id")
    private int anwesenheitId;

    @SerializedName("buchung_id")
    private int buchungId;  // Verknüpfung zur Buchung

    @SerializedName("anzahl_eingecheckt")
    private int anzahlEingecheckt;  // Wie viele Personen bei diesem Check-In

    @SerializedName("checkin_zeit")
    private String checkinZeit;  // Zeitstempel vom Server

    public int getAnwesenheitId() { return anwesenheitId; }
    public int getBuchungId() { return buchungId; }
    public int getAnzahlEingecheckt() { return anzahlEingecheckt; }
    public String getCheckinZeit() { return checkinZeit; }
}