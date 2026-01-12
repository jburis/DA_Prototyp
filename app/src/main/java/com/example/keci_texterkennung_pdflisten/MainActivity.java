package com.example.keci_texterkennung_pdflisten;

import static androidx.core.app.ActivityCompat.startActivityForResult;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

import android.graphics.Matrix;
import android.graphics.Matrix;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// PdfBox-Android
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_PDF = 1001;

    private Button btnSelectPdf;
    private TextView tvResult;

    // NEU: Merkt sich, wie viele Seiten das PDF hat
    private int pdfPageCount = -1;

    // NEU: JSON-Ausgabe als Variable speichern (zum Weitergeben an Activity/API/etc.)
    private String bookingsJson = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PDFBoxResourceLoader.init(getApplicationContext());

        btnSelectPdf = findViewById(R.id.btnSelectPdf);
        tvResult = findViewById(R.id.tvResult);

        btnSelectPdf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPdfPicker();
            }
        });
    }

    private void openPdfPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "PDF auswählen"), REQUEST_CODE_PICK_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_PICK_PDF && resultCode == RESULT_OK && data != null) {
            Uri pdfUri = data.getData();
            if (pdfUri != null) {
                tvResult.setText("PDF geladen, verarbeite...");
                extractTextWithPdfBox(pdfUri);
            }
        }
    }

    /**
     * Liest den reinen Text aller Seiten der PDF mit PdfBox,
     * parst Eventdaten + Teilnehmer und zeigt einige Beispiele an.
     */
    private void extractTextWithPdfBox(Uri pdfUri) {
        try {
            InputStream is = getContentResolver().openInputStream(pdfUri);
            if (is == null) {
                tvResult.setText("Fehler: Konnte PDF nicht öffnen (InputStream null).");
                return;
            }

            PDDocument document = PDDocument.load(is);
            pdfPageCount = document.getNumberOfPages();

            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(document);

            document.close();
            is.close();

            String parsed = parseKwpList(fullText);
            tvResult.setText(parsed);

        } catch (Exception e) {
            e.printStackTrace();
            tvResult.setText("Fehler beim Lesen der PDF: " + e.getMessage());
        }
    }

    private static class Participant {
        String firstName;
        String lastName;
        String orderNumber;
        int seats;
        String contact;
    }

    /**
     * Parst die KWP-Teilnehmerliste:
     * - Datum + Ort
     * - Eventtitel (nach dem Bindestrich)
     * - Für einige Teilnehmer: Nachname, Vorname, Bestellnummer, Plätze, Kontakt
     */
    private String parseKwpList(String fullText) {
        // 1) Zeilen vorbereiten
        String[] rawLines = fullText.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            String t = l.trim();
            if (!t.isEmpty()) {
                lines.add(t);
            }
        }

        // 2) Event-Daten finden
        String eventDate = "";
        String eventLocation = "";
        String eventTitle = "";

        // Datum + Ort: "09.10.2024, Externer Veranstaltungsort"
        Pattern datePattern = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4}),\\s*(.+)");
        for (String line : lines) {
            Matcher m = datePattern.matcher(line);
            if (m.find()) {
                eventDate = m.group(1);
                eventLocation = m.group(2);
                break;
            }
        }

        // Titel: "Teilnehmerliste – Kaiser Wiesn 2024" -> alles nach dem Bindestrich
        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.startsWith("teilnehmerliste")) {
                String[] parts = line.split("[-–]", 2);
                if (parts.length == 2) {
                    eventTitle = parts[1].trim();
                } else {
                    eventTitle = line.trim();
                }
                break;
            }
        }

        // 3) Teilnehmer-Daten sammeln
        List<Participant> participants = new ArrayList<>();
        Pattern orderPattern = Pattern.compile("#(\\d+)\\s+(\\d+)\\s+(.+)"); // #Nr Plätze Kontakt

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = orderPattern.matcher(line);
            if (!m.find()) {
                continue;
            }

            // --- Bestellnummer, Plätze, Kontakt aus der Zeile mit '#' ---
            String orderNumber = "#" + m.group(1);
            int seats = Integer.parseInt(m.group(2));
            String contact = m.group(3);

            // Telefonnummer evtl. aus der nächsten Zeile anhängen
            if (i + 1 < lines.size()) {
                String next = lines.get(i + 1).trim();
                if (next.matches(".*\\d.*") && !next.contains("@")) {
                    contact = contact + ", " + next;
                }
            }

            // --- Name suchen ---
            String nameLine = null;

            // 1) Versuch: Name in DERSELBEN Zeile vor dem '#'
            int hashIndex = line.indexOf('#');
            if (hashIndex > 0) {
                String prefix = line.substring(0, hashIndex).trim(); // alles vor "#"
                if (isLikelyName(prefix)) {
                    nameLine = prefix;
                }
            }

            // 2) Fallback: nach oben wandern, bis eine Zeile wie ein Name aussieht
            if (nameLine == null) {
                int j = i - 1;
                while (j >= 0) {
                    String cand = lines.get(j).trim();
                    String lc = cand.toLowerCase();

                    // Dinge überspringen, die sicher kein Name sind
                    if (lc.startsWith("abfahrtsstelle ausflug")) {
                        j--;
                        continue;
                    }
                    if (lc.startsWith("teilnehmer") || lc.startsWith("teilnehmerliste")) {
                        break;
                    }

                    if (isLikelyName(cand)) {
                        nameLine = cand;
                        break;
                    }

                    j--;
                }
            }

            // Wenn wir keinen brauchbaren Namen gefunden haben, diesen Eintrag überspringen
            if (nameLine == null || nameLine.isEmpty()) {
                continue;
            }

            // Nachname + Vorname aus der Namenszeile schneiden
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

        // NEU: JSON einmal erzeugen und als Variable speichern (für Weitergabe)
        bookingsJson = toJson(participants);

        // optional: Logcat zum Kontrollieren
        android.util.Log.d("KWP_JSON", bookingsJson);

        // 4) Ausgabe zusammenbauen (nur ein paar Beispiele)
        StringBuilder sb = new StringBuilder();
        sb.append("PDF Seiten: ").append(pdfPageCount).append("\n");
        sb.append("Datum: ").append(eventDate).append("\n");
        sb.append("Ort: ").append(eventLocation).append("\n");
        sb.append("Titel: ").append(eventTitle).append("\n\n");

        sb.append("Erkannte Teilnehmer gesamt: ")
                .append(participants.size())
                .append("\n\n");

        sb.append("Beispiel-Teilnehmer:\n");

        int max = participants.size();
        for (int i = 0; i < max; i++) {
            Participant p = participants.get(i);
            sb.append("- ").append(p.lastName).append(", ").append(p.firstName).append("\n");
            sb.append("  Bestellnummer: ").append(p.orderNumber).append("\n");
            sb.append("  Plätze: ").append(p.seats).append("\n");
            sb.append("  Kontakt: ").append(p.contact).append("\n\n");
        }

        if (participants.isEmpty()) {
            sb.append("(Keine Teilnehmer erkannt – Parser muss ggf. angepasst werden)\n");
        }

        // optional: JSON am Ende in der UI anzeigen (nur Debug)
        // sb.append("\n--- JSON ---\n").append(bookingsJson);

        return sb.toString();
    }

    /**
     * Optionaler Getter, falls du den JSON später (z.B. Button-Klick) brauchst.
     */
    public String getBookingsJson() {
        return bookingsJson;
    }

    /**
     * Übergibt das gerenderte PDF-Bitmap an ML Kit und zeigt den erkannten Text im TextView an.
     */
    private void runTextRecognition(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        com.google.mlkit.vision.text.TextRecognizer recognizer =
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(new OnSuccessListener<Text>() {
                    @Override
                    public void onSuccess(Text text) {
                        String raw = text.getText();

                        StringBuilder sb = new StringBuilder();
                        sb.append("PDF Seiten: ").append(pdfPageCount).append("\n");
                        sb.append("Zeichen gesamt: ").append(raw.length()).append("\n\n");
                        sb.append(raw);

                        tvResult.setText(sb.toString());
                    }

                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        tvResult.setText("Fehler bei der Texterkennung: " + e.getMessage());
                    }
                });
    }

    /**
     * Prüft grob, ob eine Zeile wie ein Personenname aussieht.
     */
    private boolean isLikelyName(String cand) {
        if (cand == null) return false;
        String trimmed = cand.trim();
        if (trimmed.isEmpty()) return false;

        String lc = trimmed.toLowerCase();

        // Dinge ausschließen, die sicher kein Name sind
        if (trimmed.contains("@") || trimmed.contains("#") || trimmed.contains("/") || trimmed.contains(",")) {
            return false;
        }
        // keine Ziffern im Namen
        if (trimmed.matches(".*\\d.*")) {
            return false;
        }

        // Schlagwörter, die auf Haus/Klub/Organisation hindeuten
        String[] bannedWords = {
                "haus", "klub", "klubs", "pensionisten", "pensionistinnen",
                "stadt", "wien", "kwp", "gruppe", "team"
        };
        for (String b : bannedWords) {
            if (lc.contains(b)) {
                return false;
            }
        }

        // Anzahl Wörter: typischerweise 2–4
        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 2 || tokens.length > 4) {
            return false;
        }

        // die ersten 1–2 Wörter sollten mit Großbuchstaben beginnen
        for (int i = 0; i < Math.min(2, tokens.length); i++) {
            if (tokens[i].isEmpty()) continue;
            char c0 = tokens[i].charAt(0);
            if (!Character.isUpperCase(c0)) {
                return false;
            }
        }

        return true;
    }

    private String toJson(List<Participant> participants) {
        StringBuilder sb = new StringBuilder();

        sb.append("{\n");
        sb.append("  \"buchungen\": [\n");

        for (int i = 0; i < participants.size(); i++) {
            Participant p = participants.get(i);

            sb.append("    {\n");
            sb.append("      \"bestellnummer\": \"").append(p.orderNumber).append("\",\n");
            sb.append("      \"vorname\": \"").append(escapeJson(p.firstName)).append("\",\n");
            sb.append("      \"nachname\": \"").append(escapeJson(p.lastName)).append("\",\n");
            sb.append("      \"kontakt\": \"").append(escapeJson(p.contact)).append("\",\n");
            sb.append("      \"anzahl_plaetze\": ").append(p.seats).append("\n");
            sb.append("    }");

            if (i < participants.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
