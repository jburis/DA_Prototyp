package com.example.da_prototyp_ocr.model;

import com.google.gson.annotations.SerializedName;

/**
 * Zentrale Datenklasse: Eine Buchung = ein Teilnehmer mit seinen gebuchten Plätzen.
 * Kommt direkt von der API und wird überall in der App verwendet.
 */
public class Buchung {

    @SerializedName("buchung_id")
    private int buchungId;

    @SerializedName("veranstaltung_id")
    private int veranstaltungId;

    @SerializedName("bestellnummer")
    private String bestellnummer;  // z.B. "#98452" – wird für OCR-Matching verwendet

    @SerializedName("vorname")
    private String vorname;

    @SerializedName("nachname")
    private String nachname;

    @SerializedName("kontakt")
    private String kontakt;  // E-Mail-Adresse

    @SerializedName("anzahl_plaetze")
    private int anzahlPlaetze;  // Wie viele Plätze gebucht wurden

    // Nicht von der API – wird lokal berechnet aus den Anwesenheiten
    private int checkedInCount;

    // ==================== Getter ====================

    public int getBuchungId() { return buchungId; }
    public int getVeranstaltungId() { return veranstaltungId; }
    public String getBestellnummer() { return bestellnummer; }
    public String getVorname() { return vorname; }
    public String getNachname() { return nachname; }
    public String getKontakt() { return kontakt; }
    public int getAnzahlPlaetze() { return anzahlPlaetze; }
    public int getCheckedInCount() { return checkedInCount; }

    /**
     * Gibt "Vorname Nachname" zurück – wird in der UI und beim QR-Matching verwendet.
     */
    public String getDisplayName() {
        return (vorname == null ? "" : vorname) + " " + (nachname == null ? "" : nachname);
    }

    // ==================== Setter ====================

    public void setBuchungId(int buchungId) { this.buchungId = buchungId; }
    public void setVeranstaltungId(int veranstaltungId) { this.veranstaltungId = veranstaltungId; }
    public void setBestellnummer(String bestellnummer) { this.bestellnummer = bestellnummer; }
    public void setVorname(String vorname) { this.vorname = vorname; }
    public void setNachname(String nachname) { this.nachname = nachname; }
    public void setKontakt(String kontakt) { this.kontakt = kontakt; }
    public void setAnzahlPlaetze(int anzahlPlaetze) { this.anzahlPlaetze = anzahlPlaetze; }

    /**
     * Wird nach dem Laden der Anwesenheiten gesetzt.
     * Zeigt wie viele der gebuchten Plätze bereits eingecheckt sind.
     */
    public void setCheckedInCount(int checkedInCount) { this.checkedInCount = checkedInCount; }
}