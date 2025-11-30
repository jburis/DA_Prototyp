package com.example.keci_texterkennung_pdflisten;

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

            String orderNumber = "#" + m.group(1);
            int seats = Integer.parseInt(m.group(2));
            String contact = m.group(3);

            // Mögliche Telefonnummer in der nächsten Zeile anhängen
            if (i + 1 < lines.size()) {
                String next = lines.get(i + 1);
                if (next.matches(".*\\d.*") && !next.contains("@")) {
                    contact = contact + ", " + next;
                }
            }

            // Name nach oben suchen, "Abfahrtsstelle..." überspringen
            String nameLine = "";
            int j = i - 1;
            while (j >= 0) {
                String cand = lines.get(j);
                String lc = cand.toLowerCase();

                if (lc.startsWith("abfahrtsstelle ausflug")) {
                    j--;
                    continue;
                }
                if (lc.startsWith("teilnehmer ") || lc.startsWith("teilnehmerliste")) {
                    break;
                }
                nameLine = cand;
                break;
            }

            if (nameLine.isEmpty()) {
                continue;
            }

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

        // 4) Ausgabe zusammenbauen (nur ein paar Beispiele)
        StringBuilder sb = new StringBuilder();
        sb.append("PDF Seiten: ").append(pdfPageCount).append("\n");
        sb.append("Datum: ").append(eventDate).append("\n");
        sb.append("Ort: ").append(eventLocation).append("\n");
        sb.append("Titel: ").append(eventTitle).append("\n\n");

        sb.append("Beispiel-Teilnehmer:\n");

        int max = Math.min(participants.size(), 5); // nur die ersten paar anzeigen
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

        return sb.toString();
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
}
