package com.example.da_prototyp_ocr.logic;

import com.example.da_prototyp_ocr.dto.ImportBuchungItem;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser für KWP-Teilnehmerlisten im PDF-Format.
 * Extrahiert Veranstaltungsdaten und Buchungen aus dem PDF-Textinhalt.
 */
public class PdfParser {

    /**
     * Ergebnis des Parsing-Vorgangs.
     */
    public static class ParseResult {
        public final String eventTitle;
        public final String eventLocation;
        public final String eventDateIso;  // YYYY-MM-DD
        public final String eventDateOriginal;  // DD.MM.YYYY
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

        public boolean isValid() {
            return !eventTitle.isEmpty() && !eventLocation.isEmpty()
                    && !eventDateIso.isEmpty() && !buchungen.isEmpty();
        }
    }

    /**
     * Interne Hilfsklasse für geparste Teilnehmerdaten.
     */
    private static class Participant {
        String firstName;
        String lastName;
        String orderNumber;
        int seats;
        String contact;
    }

    /**
     * Parst den Textinhalt einer KWP-Teilnehmerliste.
     *
     * @param fullText Der extrahierte Text aus dem PDF
     * @return ParseResult mit Veranstaltungsdaten und Buchungen
     */
    public ParseResult parse(String fullText) {
        String[] rawLines = fullText.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            String t = l.trim();
            if (!t.isEmpty()) lines.add(t);
        }

        // 1) Veranstaltungsdaten extrahieren
        String eventDate = "";
        String eventLocation = "";
        String eventTitle = "";

        // Datum und Ort aus Zeile "DD.MM.YYYY, Ortsname"
        Pattern datePattern = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4}),\\s*(.+)");
        for (String line : lines) {
            Matcher m = datePattern.matcher(line);
            if (m.find()) {
                eventDate = m.group(1);
                eventLocation = m.group(2);
                break;
            }
        }

        // Titel aus Zeile "Teilnehmerliste - Veranstaltungsname"
        // Unterstützt auch mehrzeilige Titel (z.B. "Ausflug nach Schönberg,\nStraußenfarm")
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String lower = line.toLowerCase();
            if (lower.startsWith("teilnehmerliste")) {
                String[] parts = line.split("[-–]", 2);
                if (parts.length == 2) {
                    eventTitle = parts[1].trim();

                    // Prüfen ob nächste Zeile eine Titel-Fortsetzung ist
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

        // 2) Buchungen extrahieren
        List<Participant> participants = new ArrayList<>();
        Pattern orderPattern = Pattern.compile("#(\\d+)\\s+(\\d+)\\s+(.+)");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = orderPattern.matcher(line);
            if (!m.find()) continue;

            String orderNumber = "#" + m.group(1);
            int seats = Integer.parseInt(m.group(2));
            String contact = m.group(3);

            // Zusätzliche Kontaktinfo in nächster Zeile?
            if (i + 1 < lines.size()) {
                String next = lines.get(i + 1).trim();
                if (next.matches(".*\\d.*") && !next.contains("@")) {
                    contact = contact + ", " + next;
                }
            }

            // Name finden (Rückwärtssuche)
            String nameLine = findNameBackwards(lines, i, line);

            if (nameLine == null || nameLine.isEmpty()) continue;

            // Name bereinigen
            nameLine = cleanName(nameLine);
            nameLine = cleanNameFromExtras(nameLine);

            // Name in Vor- und Nachname splitten
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

        // 3) Ergebnis zusammenstellen
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
     * Sucht rückwärts ab der Bestellnummern-Zeile nach dem Teilnehmernamen.
     */
    private String findNameBackwards(List<String> lines, int orderLineIndex, String orderLine) {
        // Erst prüfen ob Name vor # in derselben Zeile steht
        int hashIndex = orderLine.indexOf('#');
        if (hashIndex > 0) {
            String prefix = orderLine.substring(0, hashIndex).trim();
            if (isLikelyName(prefix)) return prefix;
        }

        // Rückwärtssuche in vorherigen Zeilen
        int j = orderLineIndex - 1;
        while (j >= 0) {
            String cand = lines.get(j).trim();

            // Zeilen überspringen die keine Namen sind
            if (isSkipLine(cand)) {
                j--;
                continue;
            }

            // Abbruchbedingung: Headerzeilen
            String lc = cand.toLowerCase();
            if (lc.startsWith("teilnehmer") || lc.startsWith("teilnehmerliste")) {
                break;
            }

            if (isLikelyName(cand)) {
                return cand;
            }
            j--;
        }

        return null;
    }

    /**
     * Prüft ob eine Zeile übersprungen werden soll (keine Namenszeile).
     */
    private boolean isSkipLine(String line) {
        if (line == null || line.isEmpty()) return true;
        String lc = line.toLowerCase();

        // Zusatzinfos überspringen
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
     * Prüft ob eine Zeile eine Fortsetzung des Event-Titels ist.
     * Wird verwendet wenn der Titel über mehrere Zeilen geht.
     * z.B. "Teilnehmerliste – Ausflug nach Schönberg," / "Straußenfarm"
     *
     * @param line Die zu prüfende Zeile
     * @return true wenn die Zeile wahrscheinlich zum Titel gehört
     */
    private boolean isTitleContinuation(String line) {
        if (line == null || line.trim().isEmpty()) return false;
        String trimmed = line.trim();
        String lc = trimmed.toLowerCase();

        // Tabellen-Header-Zeile erkennen
        if (lc.contains("teilnehmer") && lc.contains("bestellung")) return false;
        if (lc.contains("plätze") && lc.contains("kontakt")) return false;

        // Bestellnummer-Muster (#123456)
        if (trimmed.matches(".*#\\d{5,}.*")) return false;

        // E-Mail-Adresse
        if (trimmed.contains("@")) return false;

        // Teilnehmername mit Sternchen-Präfix (z.B. *Borowa *Elzbieta)
        if (trimmed.startsWith("*")) return false;

        // Zeile die wie ein Name aussieht (Nachname Vorname)
        // Wenn es ein valider Name ist, gehört es nicht zum Titel
        if (isLikelyName(trimmed)) return false;

        // Sehr lange Zeilen sind unwahrscheinlich Titel-Fortsetzungen
        if (trimmed.length() > 60) return false;

        // Sehr kurze Zeilen (1-2 Zeichen) sind keine Titel
        if (trimmed.length() < 3) return false;

        return true;
    }

    /**
     * Prüft ob eine Zeile wahrscheinlich ein Personenname ist.
     */
    private boolean isLikelyName(String cand) {
        if (cand == null) return false;
        String trimmed = cand.trim();
        if (trimmed.isEmpty()) return false;

        String lc = trimmed.toLowerCase();

        // Sonderzeichen ausschließen
        if (trimmed.contains("@") || trimmed.contains("#") ||
                trimmed.contains("/") || trimmed.contains(",")) return false;
        if (trimmed.matches(".*\\d.*")) return false;

        // Bannwörter
        String[] bannedWords = {
                // Organisationen
                "haus", "klub", "klubs", "pensionisten", "pensionistinnen",
                "stadt", "wien", "kwp", "gruppe", "team", "fonds", "kuratorium",
                // Speisen
                "hühnerbrust", "schweineschnitzel", "rindsbraten", "fisch",
                "suppe", "salat", "dessert", "menü", "vegetarisch",
                // Sonstiges
                "begleitperson", "abfahrtsstelle", "ausflug", "teilnehmer"
        };
        for (String b : bannedWords) {
            if (lc.contains(b)) return false;
        }

        // Mindestens 2, maximal 4 Wörter
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 2 || tokens.length > 4) return false;

        // Erste Wörter müssen mit Großbuchstabe beginnen
        for (int i = 0; i < Math.min(2, tokens.length); i++) {
            if (tokens[i].isEmpty()) continue;
            char first = tokens[i].charAt(0);
            // Auch Sternchen erlauben (*Borowa)
            if (first == '*' && tokens[i].length() > 1) {
                first = tokens[i].charAt(1);
            }
            if (!Character.isUpperCase(first)) return false;
        }

        return true;
    }

    /**
     * Entfernt Sternchen-Präfixe aus Namen (*Borowa *Elzbieta -> Borowa Elzbieta).
     */
    private String cleanName(String name) {
        if (name == null) return "";
        return name.replaceAll("\\*", "").trim().replaceAll("\\s+", " ");
    }

    /**
     * Entfernt Zusatzinfos aus Namen (z.B. "Braun mit Rollator Alfons" -> "Braun Alfons").
     */
    private String cleanNameFromExtras(String name) {
        if (name == null) return "";
        // Entferne "mit Rollator", "mit Rollstuhl", etc.
        return name.replaceAll("(?i)\\s+mit\\s+\\w+", "").trim();
    }

    /**
     * Konvertiert deutsches Datum (DD.MM.YYYY) in ISO-Format (YYYY-MM-DD).
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