package com.example.da_prototyp_ocr;

import com.google.gson.annotations.SerializedName;

public class ImportBuchungenResponse {
    @SerializedName("veranstaltung_id")
    public int veranstaltungId;

    @SerializedName("importiert")
    public int importiert;

    @SerializedName("duplikate")
    public int duplikate;
}
