package com.example.da_prototyp_ocr.logic;

import com.example.da_prototyp_ocr.model.Buchung;

public class CheckInManager {

    public int freeSeats(Buchung b) {
        if (b == null) return 0;
        int free = b.getAnzahlPlaetze() - b.getCheckedInCount();
        return Math.max(0, free);
    }

    public int clampAmount(Buchung b, int desired) {
        int free = freeSeats(b);
        if (free <= 0) return 0;
        if (desired < 1) return 1;
        return Math.min(desired, free);
    }

    public boolean canIncrease(Buchung b, int currentAmount) {
        int free = freeSeats(b);
        return free > 0 && currentAmount < free;
    }

    public boolean canDecrease(int currentAmount) {
        return currentAmount > 1;
    }
}