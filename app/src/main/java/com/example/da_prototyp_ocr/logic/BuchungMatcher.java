package com.example.da_prototyp_ocr.logic;

import androidx.annotation.Nullable;

import com.example.da_prototyp_ocr.model.Buchung;

import java.util.List;

/**
 * Sucht Buchungen in der Liste – entweder per Bestellnummer (OCR) oder per Name (QR-Code).
 * Wird vom CombinedAnalyzer verwendet um erkannte Werte mit den geladenen Buchungen abzugleichen.
 */
public class BuchungMatcher {

    /**
     * Sucht eine Buchung anhand der Bestellnummer (z.B. "#98452").
     * Wird verwendet wenn OCR eine Bestellnummer auf der Buchungsbestätigung erkennt.
     */
    @Nullable
    public Buchung findByBestellnummer(List<Buchung> list, String bestellnummer) {
        if (list == null || bestellnummer == null) return null;

        String target = bestellnummer.trim();
        for (Buchung b : list) {
            if (b != null && b.getBestellnummer() != null && target.equals(b.getBestellnummer().trim())) {
                return b;
            }
        }
        return null;
    }

    /**
     * Sucht eine Buchung anhand des Namens.
     * Wird verwendet wenn QR-Code einen Namen aus der Klubkarte liefert.
     * Vergleich ist case-insensitive und ignoriert doppelte Leerzeichen.
     */
    @Nullable
    public Buchung findByDisplayName(List<Buchung> list, String participantName) {
        if (list == null || participantName == null) return null;

        String target = normalize(participantName);
        for (Buchung b : list) {
            if (b == null) continue;
            String dn = b.getDisplayName();
            if (dn != null && normalize(dn).equals(target)) return b;
        }
        return null;
    }

    /**
     * Normalisiert einen String für Vergleich:
     * - Leerzeichen vorne/hinten weg
     * - Mehrfache Leerzeichen zu einem
     * - Alles lowercase
     */
    private String normalize(String s) {
        return s.trim().replaceAll("\\s+", " ").toLowerCase();
    }
}