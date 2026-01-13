package com.example.da_prototyp_ocr;

import com.google.gson.annotations.SerializedName;

public class Veranstaltung {

    @SerializedName("veranstaltung_id")
    private int veranstaltungId;

    @SerializedName("veranstaltung_name")
    private String veranstaltungName;

    @SerializedName("veranstaltung_datum")
    private String veranstaltungDatum;

    @SerializedName("veranstaltung_ort")
    private String veranstaltungOrt;

    public int getVeranstaltungId() { return veranstaltungId; }
    public String getVeranstaltungName() { return veranstaltungName; }
    public String getVeranstaltungDatum() { return veranstaltungDatum; }
    public String getVeranstaltungOrt() { return veranstaltungOrt; }
}
