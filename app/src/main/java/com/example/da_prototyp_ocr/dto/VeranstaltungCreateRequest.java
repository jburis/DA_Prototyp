package com.example.da_prototyp_ocr.dto;

/**
 * Request-Body für das Erstellen einer neuen Veranstaltung.
 * Wird an POST /api/veranstaltungen geschickt.
 */
public class VeranstaltungCreateRequest {

    public String veranstaltung_name;    // z.B. "Sommerfest 2025"
    public String veranstaltung_datum;   // Format: YYYY-MM-DD (z.B. "2025-07-15")
    public String veranstaltung_ort;     // z.B. "Klubhaus Leopoldstadt"

    public VeranstaltungCreateRequest(String name, String datumIso, String ort) {
        this.veranstaltung_name = name;
        this.veranstaltung_datum = datumIso;
        this.veranstaltung_ort = ort;
    }
}