package com.example.da_prototyp_ocr.dto;

/**
 * Einzelne Buchung für den Massenimport.
 * Wird aus der PDF extrahiert und dann als Teil von ImportBuchungenRequest an die API geschickt.
 */
public class ImportBuchungItem {

    public String bestellnummer;   // z.B. "#98452"
    public String vorname;
    public String nachname;
    public String kontakt;         // E-Mail-Adresse aus der PDF
    public int anzahl_plaetze;     // Wie viele Plätze gebucht wurden

    public ImportBuchungItem(String bestellnummer, String vorname, String nachname,
                             String kontakt, int anzahl_plaetze) {
        this.bestellnummer = bestellnummer;
        this.vorname = vorname;
        this.nachname = nachname;
        this.kontakt = kontakt;
        this.anzahl_plaetze = anzahl_plaetze;
    }
}