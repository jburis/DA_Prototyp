package com.example.da_prototyp_ocr.logic;

import com.example.da_prototyp_ocr.dto.ImportBuchungItem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser für die KWP-Teilnehmerlisten im PDF-Format.
 *
 * Das PDF vom Auftraggeber hat ein spezielles Format:
 * - Oben steht Datum und Ort (z.B. "15.07.2025, Klubhaus Leopoldstadt")
 * - Dann der Titel (z.B. "Teilnehmerliste – Sommerfest")
 * - Darunter die Teilnehmer mit Bestellnummer, Plätzen und Kontakt
 *
 * Der Parser extrahiert all diese Daten und gibt sie als ParseResult zurück.
 */
public class PdfParser {

    /**
     * Enthält alle extrahierten Daten aus dem PDF.
     */
    public static class ParseResult {
        public final String eventTitle;
        public final String eventLocation;
        public final String eventDateIso;       // YYYY-MM-DD (für API)
        public final String eventDateOriginal;  // DD.MM.YYYY (zur Anzeige)
        public final List<ImportBuchungItem> buchungen;
        public final int participantCount;

        public ParseResult(String eventTitle, String eventLocation, String eventDateIso,
                           String eventDateOriginal, List<ImportBuchungItem> buchungen) {
            this.eventTitle = eventTitle;
            this.eventLocation = eventLocation;
            this.eventDateIso = eventDateIso;
            this.eventDateOriginal = eventDateOriginal;
            this.buchungen = buchungen;
            this.participantCount = buchungen.size();
        }

        /**
         * Prüft ob das Parsing erfolgreich war (alle Pflichtfelder gefüllt).
         */
        public boolean isValid() {
            return !eventTitle.isEmpty() && !eventLocation.isEmpty()
                    && !eventDateIso.isEmpty() && !buchungen.isEmpty();
        }
    }

    /**
     * Zwischenspeicher für geparste Teilnehmerdaten, bevor sie zu ImportBuchungItem werden.
     */
    private static class Participant {
        String firstName;
        String lastName;
        String orderNumber;
        int seats;
        String contact;
    }

    /**
     * Hauptmethode: Parst den kompletten PDF-Text und extrahiert alle Daten.
     *
     * @param fullText Der mit PDFBox extrahierte Text
     * @return ParseResult mit Veranstaltung und allen Buchungen
     */
    public ParseResult parse(String fullText) {
        // Text in Zeilen aufteilen und leere Zeilen entfernen
        String[] rawLines = fullText.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            String t = l.trim();
            if (!t.isEmpty()) lines.add(t);
        }

        // ==================== 1) VERANSTALTUNGSDATEN ====================

        String eventDate = "";
        String eventLocation = "";
        String eventTitle = "";

        // Datum und Ort: Suche nach "DD.MM.YYYY, Ortsname"
        Pattern datePattern = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4}),\\s*(.+)");
        for (String line : lines) {
            Matcher m = datePattern.matcher(line);
            if (m.find()) {
                eventDate = m.group(1);      // z.B. "15.07.2025"
                eventLocation = m.group(2);  // z.B. "Klubhaus Leopoldstadt"
                break;
            }
        }

        // Titel: Suche nach "Teilnehmerliste - Veranstaltungsname"
        // Manchmal geht der Titel über 2 Zeilen (z.B. "Ausflug nach Schönberg,\nStraußenfarm")
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String lower = line.toLowerCase();
            if (lower.startsWith("teilnehmerliste")) {
                String[] parts = line.split("[-–]", 2);  // Bindestrich oder Gedankenstrich
                if (parts.length == 2) {
                    eventTitle = parts[1].trim();

                    // Checken ob nächste Zeile noch zum Titel gehört
                    if (i + 1 < lines.size()) {
                        String nextLine = lines.get(i + 1).trim();
                        if (isTitleContinuation(nextLine)) {
                            eventTitle = eventTitle + " " + nextLine;
                        }
                    }
                } else {
                    eventTitle = line.trim();
                }
                break;
            }
        }

        // ==================== 2) BUCHUNGEN EXTRAHIEREN ====================

        List<Participant> participants = new ArrayList<>();

        // Bestellnummern-Zeilen haben das Format: "#98452 1 email@example.com"
        Pattern orderPattern = Pattern.compile("#(\\d+)\\s+(\\d+)\\s+(.+)");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = orderPattern.matcher(line);
            if (!m.find()) continue;

            // Bestelldaten aus der Zeile extrahieren
            String orderNumber = "#" + m.group(1);
            int seats = Integer.parseInt(m.group(2));
            String contact = m.group(3);

            // Manchmal steht noch Zusatzinfo (Telefonnummer) in der nächsten Zeile
            if (i + 1 < lines.size()) {
                String next = lines.get(i + 1).trim();
                if (next.matches(".*\\d.*") && !next.contains("@")) {
                    contact = contact + ", " + next;
                }
            }

            // Name steht VOR der Bestellnummern-Zeile → rückwärts suchen
            String nameLine = findNameBackwards(lines, i, line);
            if (nameLine == null || nameLine.isEmpty()) continue;

            // Name aufräumen (Sternchen, "mit Rollator" etc. entfernen)
            nameLine = cleanName(nameLine);
            nameLine = cleanNameFromExtras(nameLine);

            // Im PDF steht "Nachname Vorname", also splitten
            String[] nameParts = nameLine.split("\\s+", 2);
            String lastName = nameParts[0];
            String firstName = (nameParts.length > 1) ? nameParts[1] : "";

            Participant p = new Participant();
            p.firstName = firstName;
            p.lastName = lastName;
            p.orderNumber = orderNumber;
            p.seats = seats;
            p.contact = contact;
            participants.add(p);
        }

        // ==================== 3) ERGEBNIS ZUSAMMENSTELLEN ====================

        List<ImportBuchungItem> buchungen = new ArrayList<>();
        for (Participant p : participants) {
            buchungen.add(new ImportBuchungItem(
                    p.orderNumber,
                    p.firstName,
                    p.lastName,
                    p.contact,
                    p.seats
            ));
        }

        return new ParseResult(
                eventTitle,
                eventLocation,
                toIsoDate(eventDate),
                eventDate,
                buchungen
        );
    }

    /**
     * Sucht rückwärts nach dem Teilnehmernamen.
     * Der Name steht immer VOR der Bestellnummern-Zeile, aber manchmal
     * sind dazwischen noch Zeilen wie "Abfahrtsstelle" oder "Menüauswahl".
     */
    private String findNameBackwards(List<String> lines, int orderLineIndex, String orderLine) {
        // Manchmal steht der Name direkt vor dem # in derselben Zeile
        int hashIndex = orderLine.indexOf('#');
        if (hashIndex > 0) {
            String prefix = orderLine.substring(0, hashIndex).trim();
            if (isLikelyName(prefix)) return prefix;
        }

        // Rückwärts durch die vorherigen Zeilen gehen
        int j = orderLineIndex - 1;
        while (j >= 0) {
            String cand = lines.get(j).trim();

            // Irrelevante Zeilen überspringen (Menü, Abfahrtsstelle etc.)
            if (isSkipLine(cand)) {
                j--;
                continue;
            }

            // Bei Header-Zeilen aufhören (zu weit zurück)
            String lc = cand.toLowerCase();
            if (lc.startsWith("teilnehmer") || lc.startsWith("teilnehmerliste")) {
                break;
            }

            // Wenn es wie ein Name aussieht → gefunden!
            if (isLikelyName(cand)) {
                return cand;
            }
            j--;
        }

        return null;
    }

    /**
     * Zeilen die übersprungen werden sollen (keine Namen).
     * Der Auftraggeber hat das PDF-Format geändert: Zusatzinfos beginnen jetzt mit |
     */
    private boolean isSkipLine(String line) {
        if (line == null || line.isEmpty()) return true;
        String lc = line.toLowerCase();

        // Neue Zeilen mit Pipe-Präfix überspringen (neues PDF-Format)
        if (line.startsWith("|")) return true;

        // Zusatzinfos überspringen (altes Format, zur Sicherheit behalten)
        if (lc.startsWith("abfahrtsstelle")) return true;
        if (lc.startsWith("menüauswahl")) return true;
        if (lc.equals("begleitperson")) return true;

        // Menü-Zeilen überspringen
        if (lc.startsWith("menü")) return true;
        if (lc.contains("hühnerbrust") || lc.contains("schweineschnitzel")) return true;
        if (lc.contains("rindsbraten") || lc.contains("fisch")) return true;

        // Fußzeilen überspringen
        if (lc.contains("fonds kuratorium")) return true;
        if (lc.contains("pensionisten-wohnhäuser")) return true;

        return false;
    }

    /**
     * Prüft ob eine Zeile noch zum Event-Titel gehört.
     * Manche Titel gehen über 2 Zeilen, z.B.:
     * "Teilnehmerliste – Ausflug nach Schönberg,"
     * "Straußenfarm"
     */
    private boolean isTitleContinuation(String line) {
        if (line == null || line.trim().isEmpty()) return false;
        String trimmed = line.trim();
        String lc = trimmed.toLowerCase();

        // Das ist der Tabellen-Header, kein Titel
        if (lc.contains("teilnehmer") && lc.contains("bestellung")) return false;
        if (lc.contains("plätze") && lc.contains("kontakt")) return false;

        // Das sind Buchungsdaten, kein Titel
        if (trimmed.matches(".*#\\d{5,}.*")) return false;  // Bestellnummer
        if (trimmed.contains("@")) return false;            // E-Mail
        if (trimmed.startsWith("*")) return false;          // Name mit Sternchen

        // Wenn es wie ein Name aussieht, gehört es nicht zum Titel
        if (isLikelyName(trimmed)) return false;

        // Plausibilitätschecks
        if (trimmed.length() > 60) return false;  // Zu lang
        if (trimmed.length() < 3) return false;   // Zu kurz

        return true;
    }

    /**
     * Heuristik: Sieht diese Zeile wie ein Personenname aus?
     */
    private boolean isLikelyName(String cand) {
        if (cand == null) return false;
        String trimmed = cand.trim();
        if (trimmed.isEmpty()) return false;

        String lc = trimmed.toLowerCase();

        // Sonderzeichen → kein Name
        if (trimmed.contains("@") || trimmed.contains("#") ||
                trimmed.contains("/") || trimmed.contains(",")) return false;
        if (trimmed.matches(".*\\d.*")) return false;  // Keine Ziffern in Namen

        // Wörter die definitiv keine Namen sind
        String[] bannedWords = {
                // Organisationen
                "haus", "klub", "klubs", "pensionisten", "pensionistinnen",
                "stadt", "wien", "kwp", "gruppe", "team", "fonds", "kuratorium",
                // Speisen (steht manchmal im PDF)
                "hühnerbrust", "schweineschnitzel", "rindsbraten", "fisch",
                "suppe", "salat", "dessert", "menü", "vegetarisch",
                // Sonstiges
                "begleitperson", "abfahrtsstelle", "ausflug", "teilnehmer"
        };
        for (String b : bannedWords) {
            if (lc.contains(b)) return false;
        }

        // Namen haben typischerweise 2-4 Wörter (Vorname Nachname, evtl. Zweitname)
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 2 || tokens.length > 4) return false;

        // Erste 2 Wörter müssen mit Großbuchstabe beginnen
        for (int i = 0; i < Math.min(2, tokens.length); i++) {
            if (tokens[i].isEmpty()) continue;
            char first = tokens[i].charAt(0);
            // Sternchen erlauben (*Borowa = markierte Person)
            if (first == '*' && tokens[i].length() > 1) {
                first = tokens[i].charAt(1);
            }
            if (!Character.isUpperCase(first)) return false;
        }

        return true;
    }

    /**
     * Entfernt Sternchen aus Namen.
     * Im PDF sind manche Namen mit * markiert: "*Borowa *Elzbieta" → "Borowa Elzbieta"
     */
    private String cleanName(String name) {
        if (name == null) return "";
        return name.replaceAll("\\*", "").trim().replaceAll("\\s+", " ");
    }

    /**
     * Entfernt Zusatzinfos aus Namen.
     * Manche Teilnehmer haben Notizen: "Braun mit Rollator Alfons" → "Braun Alfons"
     */
    private String cleanNameFromExtras(String name) {
        if (name == null) return "";
        return name.replaceAll("(?i)\\s+mit\\s+\\w+", "").trim();
    }

    /**
     * Konvertiert deutsches Datumsformat in ISO-Format für die API.
     * "15.07.2025" → "2025-07-15"
     */
    private String toIsoDate(String ddMMyyyy) {
        if (ddMMyyyy == null) return "";
        String s = ddMMyyyy.trim();
        if (!s.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) return "";
        String dd = s.substring(0, 2);
        String mm = s.substring(3, 5);
        String yyyy = s.substring(6, 10);
        return yyyy + "-" + mm + "-" + dd;
    }
}