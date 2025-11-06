package com.example.da_prototyp_ocr; // Passen Sie das Paket an Ihr Projekt an

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

// Serializable ist nützlich, wenn Sie das Objekt zwischen Activities übergeben möchten
public class Attendee implements Serializable {

    // Wird von der API bei der Erstellung zurückgegeben
    @SerializedName("id")
    private Integer id;

    @SerializedName("participant")
    private String participant;

    // In JSON ist es "order_number", in Java verwenden wir camelCase
    @SerializedName("order_number")
    private String orderNumber;

    @SerializedName("guest_count")
    private int guestCount;

    // Optionales Feld
    @SerializedName("contact")
    private String contact;

    @SerializedName("checked_in_count")
    private int checkedInCount;

    // Wird von der Datenbank gesetzt, aber nützlich, es in der App zu haben
    @SerializedName("created_at")
    private String createdAt;

    // Konstruktor zum Erstellen eines neuen Teilnehmers in der App
    public Attendee(String participant, String orderNumber, int guestCount, String contact) {
        this.participant = participant;
        this.orderNumber = orderNumber;
        this.guestCount = guestCount;
        this.contact = contact;
    }

    // Getter und Setter (können bei Bedarf hinzugefügt werden)
    public Integer getId() {
        return id;
    }

    public String getParticipant() {
        return participant;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public int getGuestCount() {
        return guestCount;
    }

    public String getContact() {
        return contact;
    }

    public int getCheckedInCount() {
        return checkedInCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
