package com.example.da_prototyp_ocr;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PdfImportActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_PDF = 1001;

    // DEV: vorerst hardcoded wie in Postman (später sauber aus Login/Settings)
    private static final String ADMIN_TOKEN = "supersecret-token";

    private Button btnSelectPdf;
    private TextView tvResult;

    private int pdfPageCount = -1;

    // Parsed Event-Daten
    private String eventTitle = "";
    private String eventLocation = "";
    private String eventDateIso = ""; // YYYY-MM-DD

    // Parsed Buchungen
    private final List<ImportBuchungItem> parsedBuchungen = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_import);
        PDFBoxResourceLoader.init(getApplicationContext());

        btnSelectPdf = findViewById(R.id.btnSelectPdf);
        tvResult = findViewById(R.id.tvResult);

        btnSelectPdf.setOnClickListener(v -> openPdfPicker());
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

            // 1) PDF parsen (füllt eventTitle/eventLocation/eventDateIso + parsedBuchungen)
            String parsedInfoForUi = parseKwpList(fullText);
            tvResult.setText(parsedInfoForUi);

            // 2) Danach: Veranstaltung anlegen + Buchungen importieren
            if (eventTitle.isEmpty() || eventLocation.isEmpty() || eventDateIso.isEmpty()) {
                tvResult.append("\n\nFehler: Veranstaltungsdaten unvollständig – Import abgebrochen.");
                return;
            }
            if (parsedBuchungen.isEmpty()) {
                tvResult.append("\n\nFehler: Keine Buchungen erkannt – Import abgebrochen.");
                return;
            }

            createEventThenImportBookings();

        } catch (Exception e) {
            e.printStackTrace();
            tvResult.setText("Fehler beim Lesen der PDF: " + e.getMessage());
        }
    }

    private void createEventThenImportBookings() {
        ApiService api = ApiClient.getClient().create(ApiService.class);

        VeranstaltungCreateRequest req = new VeranstaltungCreateRequest(
                eventTitle,
                eventDateIso,
                eventLocation
        );

        tvResult.append("\n\nLege Veranstaltung an...");
        api.createVeranstaltung(ADMIN_TOKEN, req).enqueue(new Callback<Veranstaltung>() {
            @Override
            public void onResponse(Call<Veranstaltung> call, Response<Veranstaltung> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    tvResult.append("\nFehler beim Anlegen der Veranstaltung: " + response.code());
                    return;
                }

                int veranstaltungId = response.body().getVeranstaltungId();
                tvResult.append("\nVeranstaltung angelegt (ID: " + veranstaltungId + "). Importiere Buchungen...");

                ImportBuchungenRequest importReq = new ImportBuchungenRequest(parsedBuchungen);
                api.importBuchungen(ADMIN_TOKEN, veranstaltungId, importReq).enqueue(new Callback<ImportBuchungenResponse>() {
                    @Override
                    public void onResponse(Call<ImportBuchungenResponse> call2, Response<ImportBuchungenResponse> response2) {
                        if (!response2.isSuccessful() || response2.body() == null) {
                            tvResult.append("\nFehler beim Import: " + response2.code());
                            return;
                        }

                        ImportBuchungenResponse r = response2.body();
                        tvResult.append("\nImport fertig. Importiert: " + r.importiert + " | Duplikate: " + r.duplikate);

                        // Zurück zur SelectListActivity -> neu laden via RESULT_OK
                        setResult(RESULT_OK);
                        finish();
                    }

                    @Override
                    public void onFailure(Call<ImportBuchungenResponse> call2, Throwable t) {
                        tvResult.append("\nNetzwerkfehler beim Import: " + t.getMessage());
                    }
                });
            }

            @Override
            public void onFailure(Call<Veranstaltung> call, Throwable t) {
                tvResult.append("\nNetzwerkfehler beim Anlegen der Veranstaltung: " + t.getMessage());
            }
        });
    }

    private static class Participant {
        String firstName;
        String lastName;
        String orderNumber;
        int seats;
        String contact;
    }

    private String parseKwpList(String fullText) {
        String[] rawLines = fullText.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        for (String l : rawLines) {
            String t = l.trim();
            if (!t.isEmpty()) lines.add(t);
        }

        String eventDate = "";
        String eventLocationLocal = "";
        String eventTitleLocal = "";

        Pattern datePattern = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4}),\\s*(.+)");
        for (String line : lines) {
            Matcher m = datePattern.matcher(line);
            if (m.find()) {
                eventDate = m.group(1);
                eventLocationLocal = m.group(2);
                break;
            }
        }

        for (String line : lines) {
            String lower = line.toLowerCase();
            if (lower.startsWith("teilnehmerliste")) {
                String[] parts = line.split("[-–]", 2);
                if (parts.length == 2) eventTitleLocal = parts[1].trim();
                else eventTitleLocal = line.trim();
                break;
            }
        }

        List<Participant> participants = new ArrayList<>();
        Pattern orderPattern = Pattern.compile("#(\\d+)\\s+(\\d+)\\s+(.+)");

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher m = orderPattern.matcher(line);
            if (!m.find()) continue;

            String orderNumber = "#" + m.group(1);
            int seats = Integer.parseInt(m.group(2));
            String contact = m.group(3);

            if (i + 1 < lines.size()) {
                String next = lines.get(i + 1).trim();
                if (next.matches(".*\\d.*") && !next.contains("@")) {
                    contact = contact + ", " + next;
                }
            }

            String nameLine = null;

            int hashIndex = line.indexOf('#');
            if (hashIndex > 0) {
                String prefix = line.substring(0, hashIndex).trim();
                if (isLikelyName(prefix)) nameLine = prefix;
            }

            if (nameLine == null) {
                int j = i - 1;
                while (j >= 0) {
                    String cand = lines.get(j).trim();
                    String lc = cand.toLowerCase();

                    if (lc.startsWith("abfahrtsstelle ausflug")) { j--; continue; }
                    if (lc.startsWith("teilnehmer") || lc.startsWith("teilnehmerliste")) break;

                    if (isLikelyName(cand)) {
                        nameLine = cand;
                        break;
                    }
                    j--;
                }
            }

            if (nameLine == null || nameLine.isEmpty()) continue;

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

        // --- WICHTIG: parsed Werte in Felder übernehmen (für API Calls) ---
        this.eventTitle = eventTitleLocal;
        this.eventLocation = eventLocationLocal;
        this.eventDateIso = toIsoDate(eventDate);

        this.parsedBuchungen.clear();
        for (Participant p : participants) {
            parsedBuchungen.add(new ImportBuchungItem(
                    p.orderNumber,
                    p.firstName,
                    p.lastName,
                    p.contact,
                    p.seats
            ));
        }

        // UI-Ausgabe (nur Info)
        StringBuilder sb = new StringBuilder();
        sb.append("PDF Seiten: ").append(pdfPageCount).append("\n");
        sb.append("Titel: ").append(eventTitleLocal).append("\n");
        sb.append("Ort: ").append(eventLocationLocal).append("\n");
        sb.append("Datum: ").append(eventDate).append(" (ISO: ").append(this.eventDateIso).append(")\n\n");
        sb.append("Erkannte Buchungen: ").append(participants.size()).append("\n");

        return sb.toString();
    }

    private String toIsoDate(String ddMMyyyy) {
        // erwartet "dd.MM.yyyy" -> "yyyy-MM-dd"
        if (ddMMyyyy == null) return "";
        String s = ddMMyyyy.trim();
        if (!s.matches("\\d{2}\\.\\d{2}\\.\\d{4}")) return "";
        String dd = s.substring(0, 2);
        String mm = s.substring(3, 5);
        String yyyy = s.substring(6, 10);
        return yyyy + "-" + mm + "-" + dd;
    }

    private boolean isLikelyName(String cand) {
        if (cand == null) return false;
        String trimmed = cand.trim();
        if (trimmed.isEmpty()) return false;

        String lc = trimmed.toLowerCase();

        if (trimmed.contains("@") || trimmed.contains("#") || trimmed.contains("/") || trimmed.contains(",")) return false;
        if (trimmed.matches(".*\\d.*")) return false;

        String[] bannedWords = {"haus","klub","klubs","pensionisten","pensionistinnen","stadt","wien","kwp","gruppe","team"};
        for (String b : bannedWords) if (lc.contains(b)) return false;

        String[] tokens = trimmed.split("\\s+");
        if (tokens.length < 2 || tokens.length > 4) return false;

        for (int i = 0; i < Math.min(2, tokens.length); i++) {
            if (tokens[i].isEmpty()) continue;
            if (!Character.isUpperCase(tokens[i].charAt(0))) return false;
        }
        return true;
    }
}
