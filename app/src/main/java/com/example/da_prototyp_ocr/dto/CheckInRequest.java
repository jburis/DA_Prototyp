package com.example.da_prototyp_ocr.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Einfacher Check-In Request – nur mit Anzahl.
 * Wird aktuell nicht verwendet (stattdessen CheckinByBestellnummerRequest).
 */
public class CheckInRequest {

    @SerializedName("count")
    private int count;  // Anzahl der einzucheckenden Personen

    public CheckInRequest(int count) {
        this.count = count;
    }

    public int getCount() {
        return count;
    }
}