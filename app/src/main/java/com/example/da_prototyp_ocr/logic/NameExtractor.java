package com.example.da_prototyp_ocr.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NameExtractor {

    // 🔢 Bestellnummern-Pattern: NEUE VERSION, extrahiert das Hashtag mit.
    private static final Pattern ORDER_PATTERN = Pattern.compile(
            "(?i)(?:bestell(?:ung|nr|nummer)\\s*[:#]?\\s*)?(#\\d{5,})"
    );

    // 👤 Name (gemischt geschrieben)
    private static final Pattern NAME_MIXED = Pattern.compile(
            "\\b([A-ZÄÖÜ][a-zäöüß]+\\s+[A-ZÄÖÜ][a-zäöüß]+(?:\\s+[A-ZÄÖÜ][a-zäöüß]+)?)\\b"
    );

    // 👤 Name (ALL CAPS)
    private static final Pattern NAME_ALLCAPS = Pattern.compile(
            "\\b([A-ZÄÖÜ]{2,}\\s+[A-ZÄÖÜ]{2,}(?:\\s+[A-ZÄÖÜ]{2,})?)\\b"
    );

    private NameExtractor() {}

    /** Extrahiert die Bestellnummer (nur Ziffern), bevorzugt den Bereich um die "Bestellung"-Zeile. */
    public static String extractOrder(String fullText) {
        if (fullText == null) return null;
        String[] lines = splitLines(fullText);

        // 1) Zuerst in/nahe Zeilen mit "Bestell" suchen (präziser)
        int idx = indexOfLineContaining(lines, "(?i)bestell");
        if (idx != -1) {
            for (int i = idx; i <= Math.min(idx + 2, lines.length - 1); i++) {
                String m = firstMatch(ORDER_PATTERN, lines[i]);
                if (m != null) return m;
            }
        }

        // 2) Fallback: überall suchen
        return firstMatch(ORDER_PATTERN, fullText);
    }

    /** Extrahiert einen plausiblen Namen; bevorzugt Zeilen nahe der Bestellnummer. */
    public static String extractName(String fullText) {
        if (fullText == null) return null;
        String[] lines = splitLines(fullText);

        // Kandidaten sammeln
        List<String> candidates = new ArrayList<>();
        for (String line : lines) {
            String cleaned = stripNoise(line);
            if (cleaned.isEmpty()) continue;
            if (cleaned.matches(".*[@€\\d].*")) continue;       // E-Mail, Geld, Ziffern eher kein Name
            if (cleaned.length() < 4) continue;

            String n = firstMatch(NAME_MIXED, cleaned);
            if (n == null) n = firstMatch(NAME_ALLCAPS, cleaned);
            if (n != null) candidates.add(n);
        }

        if (candidates.isEmpty()) return null;

        // Bevorzuge Kandidaten NACH einer "Bestell"-Zeile (typisch: direkt darunter steht der Name)
        int idx = indexOfLineContaining(lines, "(?i)bestell");
        if (idx != -1) {
            for (int i = idx + 1; i <= Math.min(idx + 3, lines.length - 1); i++) {
                String cleaned = stripNoise(lines[i]);
                String n = firstMatch(NAME_MIXED, cleaned);
                if (n == null) n = firstMatch(NAME_ALLCAPS, cleaned);
                if (n != null) return n;
            }
        }

        // Sonst: erster guter Treffer
        return candidates.get(0);
    }
    /**
     * Extrahiert den Namen speziell aus einem QR-Code mit Strichpunkt-Format.
     * Nimmt an, dass der Vorname das 4. und der Nachname das 5. Element ist.
     * Ist gegen Abstürze und Formatfehler abgesichert.
     *
     * @param fullText Der gesamte Text aus dem QR-Code, z.B. "201806618;W;;Christine;FUHRMANN"
     * @return Der zusammengesetzte Name (z.B. "Christine FUHRMANN") oder null.
     */
    public static String extractQRName(String fullText) {
        if (fullText == null || fullText.trim().isEmpty()) {
            return null;
        }

        // NEUE LOGIK: Wir trennen den String am Strichpunkt (;)
        String[] parts = fullText.split(";");

        // Sicherheitsprüfung: Stellen Sie sicher, dass das Array groß genug ist.
        // Array-Indizes sind 0-basiert, also brauchen wir für den Zugriff auf Index 4 eine Länge von mindestens 5.
        if (parts.length >= 5) {
            // Greife sicher auf das vierte (parts[3]) und fünfte (parts[4]) Element zu.
            String firstName = parts[3].trim();
            String lastName = parts[4].trim();

            // Zusätzliche Prüfung, falls die Felder leer sind (wie beim zweiten Element 'W;;C...')
            if (firstName.isEmpty() || lastName.isEmpty()) {
                return null;
            }

            return firstName + " " + lastName;
        } else {
            // Wenn der QR-Code nicht das erwartete Format hat, geben wir null zurück.
            return null;
        }
    }


    // ---------- kleine Helfer ----------

    private static String[] splitLines(String text) {
        return text.replace('\r','\n').split("\n");
    }

    private static int indexOfLineContaining(String[] lines, String regex) {
        Pattern p = Pattern.compile(regex);
        for (int i = 0; i < lines.length; i++) {
            if (p.matcher(lines[i]).find()) return i;
        }
        return -1;
    }

    private static String firstMatch(Pattern p, String s) {
        if (s == null) return null;
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1).trim() : null;
    }

    /** Entfernt typische Labels/Einleitungstexte, um Namen leichter zu treffen. */
    private static String stripNoise(String s) {
        if (s == null) return "";
        return s.replaceAll("(?i)(name|vorname|nachname|teilnehmer|kunde)\\s*:\\s*", " ")
                .replaceAll("(?i)(ihre|ihren|bestellung|bestellnr|bestellnummer|nr)\\b.*", " ")
                .trim();
    }
}
