package com.example.da_prototyp_ocr.model;

import com.google.gson.annotations.SerializedName;

public class Buchung {

    @SerializedName("buchung_id")
    private int buchungId;

    @SerializedName("veranstaltung_id")
    private int veranstaltungId;

    @SerializedName("bestellnummer")
    private String bestellnummer;

    @SerializedName("vorname")
    private String vorname;

    @SerializedName("nachname")
    private String nachname;

    @SerializedName("kontakt")
    private String kontakt;

    @SerializedName("anzahl_plaetze")
    private int anzahlPlaetze;

    // NICHT von der API geliefert: berechnen wir clientseitig
    private int checkedInCount;

    // ===== GETTERS =====
    public int getBuchungId() { return buchungId; }
    public int getVeranstaltungId() { return veranstaltungId; }
    public String getBestellnummer() { return bestellnummer; }
    public String getVorname() { return vorname; }
    public String getNachname() { return nachname; }
    public String getKontakt() { return kontakt; }
    public int getAnzahlPlaetze() { return anzahlPlaetze; }

    public int getCheckedInCount() { return checkedInCount; }

    public String getDisplayName() {
        return (vorname == null ? "" : vorname) + " " + (nachname == null ? "" : nachname);
    }

    // ===== SETTERS (needed for creating new Buchung) =====
    public void setBuchungId(int buchungId) { this.buchungId = buchungId; }
    public void setVeranstaltungId(int veranstaltungId) { this.veranstaltungId = veranstaltungId; }
    public void setBestellnummer(String bestellnummer) { this.bestellnummer = bestellnummer; }
    public void setVorname(String vorname) { this.vorname = vorname; }
    public void setNachname(String nachname) { this.nachname = nachname; }
    public void setKontakt(String kontakt) { this.kontakt = kontakt; }
    public void setAnzahlPlaetze(int anzahlPlaetze) { this.anzahlPlaetze = anzahlPlaetze; }
    public void setCheckedInCount(int checkedInCount) { this.checkedInCount = checkedInCount; }
}