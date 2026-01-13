package com.example.da_prototyp_ocr;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

public class Veranstaltung implements Serializable {

    @SerializedName("veranstaltung_id")
    private int veranstaltungId;

    @SerializedName("veranstaltung_name")
    private String name;

    @SerializedName("veranstaltung_datum")
    private String datum;

    @SerializedName("veranstaltung_ort")
    private String ort;

    public int getVeranstaltungId() {
        return veranstaltungId;
    }

    public String getName() {
        return name;
    }

    public String getDatum() {
        return datum;
    }

    public String getOrt() {
        return ort;
    }

    // Wichtig für ListView-Anzeige
    @Override
    public String toString() {
        return name + " (" + ort + ")";
    }
}
