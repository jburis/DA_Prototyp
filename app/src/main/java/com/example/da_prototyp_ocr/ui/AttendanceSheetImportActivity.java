package com.example.da_prototyp_ocr.ui;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.example.da_prototyp_ocr.BuildConfig;
import com.example.da_prototyp_ocr.R;
import com.example.da_prototyp_ocr.dto.ImportBuchungenRequest;
import com.example.da_prototyp_ocr.dto.ImportBuchungenResponse;
import com.example.da_prototyp_ocr.dto.VeranstaltungCreateRequest;
import com.example.da_prototyp_ocr.logic.PdfParser;
import com.example.da_prototyp_ocr.model.Veranstaltung;
import com.example.da_prototyp_ocr.network.ApiClient;
import com.example.da_prototyp_ocr.network.ApiService;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * PDF-Import Screen: Liest eine KWP-Teilnehmerliste ein und importiert sie.
 *
 * Ablauf:
 * 1. User wählt PDF aus dem Speicher
 * 2. PDFBox extrahiert den Text
 * 3. PdfParser extrahiert Veranstaltungsdaten + Buchungen
 * 4. API: Neue Veranstaltung anlegen
 * 5. API: Buchungen massenimportieren
 * 6. Zurück zur Veranstaltungsliste
 */
public class AttendanceSheetImportActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_PDF = 1001;
    private static final String ADMIN_TOKEN = BuildConfig.ADMIN_TOKEN;

    private Button btnSelectPdf;
    private TextView tvResult;  // Zeigt Fortschritt und Ergebnis an

    private int pdfPageCount = -1;

    private final PdfParser pdfParser = new PdfParser();
    private PdfParser.ParseResult parseResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_import);

        // PDFBox muss einmal initialisiert werden
        PDFBoxResourceLoader.init(getApplicationContext());

        btnSelectPdf = findViewById(R.id.btnSelectPdf);
        tvResult = findViewById(R.id.tvResult);

        btnSelectPdf.setOnClickListener(v -> openPdfPicker());
    }

    /**
     * Öffnet den System-Datei-Picker für PDFs.
     */
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
                extractAndParsePdf(pdfUri);
            }
        }
    }

    /**
     * Hauptlogik: PDF öffnen, Text extrahieren, parsen und validieren.
     */
    private void extractAndParsePdf(Uri pdfUri) {
        try {
            InputStream is = getContentResolver().openInputStream(pdfUri);
            if (is == null) {
                tvResult.setText("Fehler: Konnte PDF nicht öffnen (InputStream null).");
                return;
            }

            // PDF mit PDFBox laden
            PDDocument document = PDDocument.load(is);
            pdfPageCount = document.getNumberOfPages();

            // Gesamten Text aus dem PDF extrahieren
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(document);

            document.close();
            is.close();

            // Text an unseren Parser übergeben
            parseResult = pdfParser.parse(fullText);

            // Ergebnis anzeigen
            displayParseResult();

            // Validierung: Sind alle nötigen Daten vorhanden?
            if (!parseResult.isValid()) {
                if (parseResult.eventTitle.isEmpty() || parseResult.eventLocation.isEmpty()
                        || parseResult.eventDateIso.isEmpty()) {
                    tvResult.append("\n\nFehler: Veranstaltungsdaten unvollständig – Import abgebrochen.");
                } else {
                    tvResult.append("\n\nFehler: Keine Buchungen erkannt – Import abgebrochen.");
                }
                return;
            }

            // Alles OK → Import starten
            createEventThenImportBookings();

        } catch (Exception e) {
            e.printStackTrace();
            tvResult.setText("Fehler beim Lesen der PDF: " + e.getMessage());
        }
    }

    /**
     * Zeigt die extrahierten Daten zur Kontrolle an.
     */
    private void displayParseResult() {
        StringBuilder sb = new StringBuilder();
        sb.append("PDF Seiten: ").append(pdfPageCount).append("\n");
        sb.append("Titel: ").append(parseResult.eventTitle).append("\n");
        sb.append("Ort: ").append(parseResult.eventLocation).append("\n");
        sb.append("Datum: ").append(parseResult.eventDateOriginal)
                .append(" (ISO: ").append(parseResult.eventDateIso).append(")\n\n");
        sb.append("Erkannte Buchungen: ").append(parseResult.participantCount).append("\n");
        tvResult.setText(sb.toString());
    }

    /**
     * Zwei API-Calls hintereinander:
     * 1. Veranstaltung anlegen
     * 2. Mit der neuen ID die Buchungen importieren
     */
    private void createEventThenImportBookings() {
        ApiService api = ApiClient.getClient().create(ApiService.class);

        // Request für neue Veranstaltung bauen
        VeranstaltungCreateRequest req = new VeranstaltungCreateRequest(
                parseResult.eventTitle,
                parseResult.eventDateIso,
                parseResult.eventLocation
        );

        tvResult.append("\n\nLege Veranstaltung an...");

        // 1. Veranstaltung anlegen
        api.createVeranstaltung(ADMIN_TOKEN, req).enqueue(new Callback<Veranstaltung>() {
            @Override
            public void onResponse(Call<Veranstaltung> call, Response<Veranstaltung> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    tvResult.append("\nFehler beim Anlegen der Veranstaltung: " + response.code());
                    return;
                }

                int veranstaltungId = response.body().getVeranstaltungId();
                tvResult.append("\nVeranstaltung angelegt (ID: " + veranstaltungId + "). Importiere Buchungen...");

                // 2. Buchungen massenimportieren
                ImportBuchungenRequest importReq = new ImportBuchungenRequest(parseResult.buchungen);

                api.importBuchungen(ADMIN_TOKEN, veranstaltungId, importReq).enqueue(new Callback<ImportBuchungenResponse>() {
                    @Override
                    public void onResponse(Call<ImportBuchungenResponse> call2, Response<ImportBuchungenResponse> response2) {
                        if (!response2.isSuccessful() || response2.body() == null) {
                            tvResult.append("\nFehler beim Import: " + response2.code());
                            return;
                        }

                        ImportBuchungenResponse r = response2.body();
                        tvResult.append("\nImport fertig. Importiert: " + r.importiert + " | Duplikate: " + r.duplikate);

                        // Erfolg → Activity schließen und zur Liste zurück
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
}