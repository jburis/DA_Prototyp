package com.example.da_prototyp_ocr.dto;

import com.google.gson.annotations.SerializedName;

/**
 * API-Response nach dem Massenimport von Buchungen.
 * Zeigt an wie viele importiert wurden und wie viele schon existierten.
 */
public class ImportBuchungenResponse {

    @SerializedName("veranstaltung_id")
    public int veranstaltungId;

    @SerializedName("importiert")
    public int importiert;  // Anzahl neu importierter Buchungen

    @SerializedName("duplikate")
    public int duplikate;   // Anzahl übersprungener Buchungen (waren schon vorhanden)
}