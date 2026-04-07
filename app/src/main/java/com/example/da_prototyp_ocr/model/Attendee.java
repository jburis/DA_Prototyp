package com.example.da_prototyp_ocr.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Älteres Teilnehmer-Model aus der ersten API-Version.
 * Wird aktuell nicht mehr verwendet – stattdessen läuft alles über Buchung.
 * Bleibt als Fallback falls die API sich nochmal ändert.
 */
public class Attendee implements Serializable {

    @SerializedName("id")
    private Integer id;

    @SerializedName("participant")
    private String participant;  // Name des Teilnehmers

    @SerializedName("order_number")
    private String orderNumber;  // Bestellnummer

    @SerializedName("guest_count")
    private int guestCount;  // Anzahl gebuchter Plätze

    @SerializedName("contact")
    private String contact;  // E-Mail oder Telefon

    @SerializedName("checked_in_count")
    private int checkedInCount;  // Bereits eingecheckte Personen

    @SerializedName("created_at")
    private String createdAt;  // Zeitstempel der Erstellung

    /**
     * Konstruktor zum manuellen Erstellen eines Teilnehmers.
     */
    public Attendee(String participant, String orderNumber, int guestCount, String contact) {
        this.participant = participant;
        this.orderNumber = orderNumber;
        this.guestCount = guestCount;
        this.contact = contact;
    }

    // Getter
    public Integer getId() { return id; }
    public String getParticipant() { return participant; }
    public String getOrderNumber() { return orderNumber; }
    public int getGuestCount() { return guestCount; }
    public String getContact() { return contact; }
    public int getCheckedInCount() { return checkedInCount; }
    public String getCreatedAt() { return createdAt; }
}