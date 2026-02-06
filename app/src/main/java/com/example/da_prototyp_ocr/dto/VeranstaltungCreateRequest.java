package com.example.da_prototyp_ocr.dto;

public class VeranstaltungCreateRequest {
    public String veranstaltung_name;
    public String veranstaltung_datum; // YYYY-MM-DD
    public String veranstaltung_ort;

    public VeranstaltungCreateRequest(String name, String datumIso, String ort) {
        this.veranstaltung_name = name;
        this.veranstaltung_datum = datumIso;
        this.veranstaltung_ort = ort;
    }
}
