package com.example.da_prototyp_ocr.logic;

import com.example.da_prototyp_ocr.model.Buchung;

/**
 * Hilfsfunktionen für die Check-In Logik.
 * Berechnet freie Plätze und validiert die +/- Buttons im Confirmation-Popup.
 */
public class CheckInManager {

    /**
     * Wie viele Plätze sind noch frei für diese Buchung?
     * z.B. 5 Plätze gebucht, 2 schon eingecheckt → 3 frei
     */
    public int freeSeats(Buchung b) {
        if (b == null) return 0;
        int free = b.getAnzahlPlaetze() - b.getCheckedInCount();
        return Math.max(0, free);  // Nie negativ zurückgeben
    }

    /**
     * Begrenzt die gewünschte Anzahl auf das was tatsächlich möglich ist.
     * Will jemand 5 Leute einchecken, aber nur 2 Plätze frei → gibt 2 zurück.
     */
    public int clampAmount(Buchung b, int desired) {
        int free = freeSeats(b);
        if (free <= 0) return 0;
        if (desired < 1) return 1;  // Mindestens 1
        return Math.min(desired, free);
    }

    /**
     * Kann der + Button gedrückt werden?
     * Nur wenn noch freie Plätze übrig sind.
     */
    public boolean canIncrease(Buchung b, int currentAmount) {
        int free = freeSeats(b);
        return free > 0 && currentAmount < free;
    }

    /**
     * Kann der - Button gedrückt werden?
     * Nur wenn aktuell mehr als 1 ausgewählt ist (mindestens 1 Person muss eingecheckt werden).
     */
    public boolean canDecrease(int currentAmount) {
        return currentAmount > 1;
    }
}