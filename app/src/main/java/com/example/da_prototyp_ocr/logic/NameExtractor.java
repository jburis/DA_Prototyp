package com.example.da_prototyp_ocr.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrahiert relevante Daten aus erkanntem Text.
 * - Bestellnummern aus OCR-Text (Buchungsbestätigungen)
 * - Namen aus QR-Codes (Klubkarten)
 */
public class NameExtractor {

    /**
     * Regex für Bestellnummern: # gefolgt von mindestens 5 Ziffern.
     * Erkennt z.B. "#98452" oder "Bestellnummer: #123456"
     * Das (?i) macht es case-insensitive.
     */
    private static final Pattern ORDER_PATTERN = Pattern.compile(
            "(?i)(?:bestell(?:ung|nr|nummer)\\s*[:#]?\\s*)?(#\\d{5,})"
    );

    // Patterns für Namens-Erkennung (aktuell nicht verwendet, aber für spätere Erweiterung)
    private static final Pattern NAME_MIXED = Pattern.compile(
            "\\b([A-ZÄÖÜ][a-zäöüß]+\\s+[A-ZÄÖÜ][a-zäöüß]+(?:\\s+[A-ZÄÖÜ][a-zäöüß]+)?)\\b"
    );
    private static final Pattern NAME_ALLCAPS = Pattern.compile(
            "\\b([A-ZÄÖÜ]{2,}\\s+[A-ZÄÖÜ]{2,}(?:\\s+[A-ZÄÖÜ]{2,})?)\\b"
    );

    private NameExtractor() {}  // Nur statische Methoden, kein Konstruktor nötig

    /**
     * Extrahiert die Bestellnummer aus OCR-Text.
     * Sucht zuerst gezielt in der Nähe von "Bestell..." Zeilen,
     * dann als Fallback im gesamten Text.
     *
     * @param fullText Der komplette OCR-erkannte Text
     * @return Die Bestellnummer inkl. # (z.B. "#98452") oder null
     */
    public static String extractOrder(String fullText) {
        if (fullText == null) return null;
        String[] lines = splitLines(fullText);

        // Strategie 1: Suche in der Nähe von "Bestell..." Zeilen (präziser)
        // Dort steht die Bestellnummer meistens direkt daneben oder 1-2 Zeilen darunter
        int idx = indexOfLineContaining(lines, "(?i)bestell");
        if (idx != -1) {
            for (int i = idx; i <= Math.min(idx + 2, lines.length - 1); i++) {
                String m = firstMatch(ORDER_PATTERN, lines[i]);
                if (m != null) return m;
            }
        }

        // Strategie 2: Fallback – im gesamten Text suchen
        return firstMatch(ORDER_PATTERN, fullText);
    }

    /**
     * Extrahiert den Namen aus einem Klubkarten-QR-Code.
     *
     * QR-Code Format: "MitgliedsID;Geschlecht;;Vorname;Nachname"
     * Beispiel: "201806618;W;;Christine;FUHRMANN" → "Christine FUHRMANN"
     *
     * @param fullText Der rohe QR-Code Inhalt
     * @return Name als "Vorname Nachname" oder null bei ungültigem Format
     */
    public static String extractQRName(String fullText) {
        if (fullText == null || fullText.trim().isEmpty()) {
            return null;
        }

        String[] parts = fullText.split(";");

        // Brauchen mindestens 5 Teile: [0]=ID, [1]=Geschlecht, [2]=leer, [3]=Vorname, [4]=Nachname
        if (parts.length >= 5) {
            String firstName = parts[3].trim();
            String lastName = parts[4].trim();

            // Leere Namen abfangen (kann bei fehlerhaften QR-Codes passieren)
            if (firstName.isEmpty() || lastName.isEmpty()) {
                return null;
            }

            return firstName + " " + lastName;
        } else {
            // Unerwartetes Format – lieber null als Müll zurückgeben
            return null;
        }
    }


    // ==================== Hilfsmethoden ====================

    /**
     * Splittet Text in Zeilen (behandelt Windows und Unix Zeilenumbrüche).
     */
    private static String[] splitLines(String text) {
        return text.replace('\r', '\n').split("\n");
    }

    /**
     * Findet den Index der ersten Zeile die das Regex-Pattern enthält.
     */
    private static int indexOfLineContaining(String[] lines, String regex) {
        Pattern p = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            if (p.matcher(lines[i]).find()) return i;
        }
        return -1;
    }

    /**
     * Gibt den ersten Regex-Match (Gruppe 1) zurück, oder null.
     */
    private static String firstMatch(Pattern p, String s) {
        if (s == null) return null;
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * Entfernt typische Labels/Einleitungstexte aus dem String.
     * Aktuell nicht verwendet, aber nützlich falls OCR mal mehr Noise liefert.
     */
    private static String stripNoise(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)(name|vorname|nachname|teilnehmer|kunde)\\s*:\\s*", " ")
                .replaceAll("(?i)(ihre|ihren|bestellung|bestellnr|bestellnummer|nr)\\b.*", " ")
                .trim();
    }
}