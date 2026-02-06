package com.example.da_prototyp_ocr.dto;

public class ImportBuchungItem {
    public String bestellnummer;
    public String vorname;
    public String nachname;
    public String kontakt;
    public int anzahl_plaetze;

    public ImportBuchungItem(String bestellnummer, String vorname, String nachname, String kontakt, int anzahl_plaetze) {
        this.bestellnummer = bestellnummer;
        this.vorname = vorname;
        this.nachname = nachname;
        this.kontakt = kontakt;
        this.anzahl_plaetze = anzahl_plaetze;
    }
}
