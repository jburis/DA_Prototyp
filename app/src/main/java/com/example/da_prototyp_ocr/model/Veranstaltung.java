package com.example.da_prototyp_ocr.model;

import com.google.gson.annotations.SerializedName;

/**
 * Repräsentiert eine Veranstaltung (Event) aus der API.
 * Wird in der Veranstaltungsliste angezeigt und dient als Container für Buchungen.
 */
public class Veranstaltung {

    @SerializedName("veranstaltung_id")
    private int veranstaltungId;

    @SerializedName("veranstaltung_name")
    private String veranstaltungName;  // z.B. "Sommerfest 2025"

    @SerializedName("veranstaltung_datum")
    private String veranstaltungDatum;  // Format: YYYY-MM-DD

    @SerializedName("veranstaltung_ort")
    private String veranstaltungOrt;  // z.B. "Klubhaus Leopoldstadt"

    // Getter
    public int getVeranstaltungId() { return veranstaltungId; }
    public String getVeranstaltungName() { return veranstaltungName; }
    public String getVeranstaltungDatum() { return veranstaltungDatum; }
    public String getVeranstaltungOrt() { return veranstaltungOrt; }
}