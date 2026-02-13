package com.example.da_prototyp_ocr.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.da_prototyp_ocr.R;
import com.example.da_prototyp_ocr.camera.CameraController;
import com.example.da_prototyp_ocr.camera.OcrAnalyzer;
import com.example.da_prototyp_ocr.camera.QrAnalyzer;
import com.example.da_prototyp_ocr.dto.CheckinByBestellnummerRequest;
import com.example.da_prototyp_ocr.logic.BuchungMatcher;
import com.example.da_prototyp_ocr.logic.CheckInManager;
import com.example.da_prototyp_ocr.logic.NameExtractor;
import com.example.da_prototyp_ocr.model.Anwesenheit;
import com.example.da_prototyp_ocr.model.Buchung;
import com.example.da_prototyp_ocr.network.ApiClient;
import com.example.da_prototyp_ocr.network.ApiService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AttendanceCheckInActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 100;
    private static final String TAG = "AttendanceCheckIn";
    private static final String ADMIN_TOKEN = "supersecret-token";

    // ===== Views (IDs müssen im activity_main.xml existieren) =====
    private PreviewView previewView;
    private View cameraCard;
    private TextView textResult;

    private Button scanBtn;        // Buchungsnr
    private Button qrBtn;          // QR-Code
    private Button btnToggleCamera;

    private TextView attendeeNameText;
    private TextView attendeeSeatsText;

    private ImageButton btnAddManual;
    private ListView scannedList;
    private ArrayAdapter<String> scannedAdapter;
    private final List<String> scannedItems = new ArrayList<>();

    // Confirmation Popup (muss im XML existieren - siehe Block unten)
    private LinearLayout confirmationLayout;
    private TextView confirmationTitleText;
    private TextView confirmationSeatsText;
    private Button btnMinus, btnPlus;
    private TextView checkInCountText;
    private Button confirmCheckInBtn;

    // ===== State =====
    private int veranstaltungId = -1;
    private int checkInAmount = 1;
    private volatile boolean isProcessing = false;

    private List<Buchung> allBuchungen = new ArrayList<>();
    private Buchung currentScannedBuchung;

    // Helpers
    private final BuchungMatcher matcher = new BuchungMatcher();
    private final CheckInManager checkInManager = new CheckInManager();

    // Camera
    private final CameraController cameraController = new CameraController();
    private ExecutorService analyzerExecutor;
    private boolean isCameraOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ===== Bind =====
        previewView = findViewById(R.id.previewView);
        cameraCard = findViewById(R.id.cameraCard);
        textResult = findViewById(R.id.textResult);

        scanBtn = findViewById(R.id.scanBtn);
        qrBtn = findViewById(R.id.btn_qr);
        btnToggleCamera = findViewById(R.id.btnToggleCamera);

        attendeeNameText = findViewById(R.id.attendeeNameText);
        attendeeSeatsText = findViewById(R.id.attendeeSeatsText);

        btnAddManual = findViewById(R.id.btnAddManual);
        scannedList = findViewById(R.id.scannedList);
        scannedAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, scannedItems);
        scannedList.setAdapter(scannedAdapter);

        // Confirmation Layout (Popup)
        confirmationLayout = findViewById(R.id.confirmationLayout);
        confirmationTitleText = findViewById(R.id.confirmationTitleText);
        confirmationSeatsText = findViewById(R.id.confirmationSeatsText);
        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);
        checkInCountText = findViewById(R.id.checkInCountText);
        confirmCheckInBtn = findViewById(R.id.confirmCheckInBtn);

        confirmationLayout.setVisibility(View.GONE);

        analyzerExecutor = Executors.newSingleThreadExecutor();

        veranstaltungId = getIntent().getIntExtra("VERANSTALTUNG_ID", -1);
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Fehler: Keine veranstaltung_id übergeben.", Toast.LENGTH_LONG).show();
        }

        // Kamera startet NICHT automatisch – so wie du es wolltest
        setCameraUi(false);

        // Dummy-Liste nur als UI-Test (kannst du später entfernen)
        seedDummyList();

        // ===== Listeners =====
        btnToggleCamera.setOnClickListener(v -> toggleCamera());

        scanBtn.setOnClickListener(v -> {
            hideConfirmation();
            if (!isCameraOn) {
                Toast.makeText(this, "Kamera ist aus. Bitte Kamera einschalten.", Toast.LENGTH_SHORT).show();
                return;
            }
            loadBuchungenAndAnwesenheiten(this::startOneShotOcr);
        });

        qrBtn.setOnClickListener(v -> {
            hideConfirmation();
            if (!isCameraOn) {
                Toast.makeText(this, "Kamera ist aus. Bitte Kamera einschalten.", Toast.LENGTH_SHORT).show();
                return;
            }
            loadBuchungenAndAnwesenheiten(this::startOneShotQr);
        });

        btnAddManual.setOnClickListener(v -> {
            scannedItems.add(0, "Neuer Teilnehmer – 00000");
            scannedAdapter.notifyDataSetChanged();
        });

        // optional: long press löscht Eintrag
        scannedList.setOnItemLongClickListener((parent, view, position, id) -> {
            scannedItems.remove(position);
            scannedAdapter.notifyDataSetChanged();
            return true;
        });

        setupPlusMinus();

        confirmCheckInBtn.setOnClickListener(v -> {
            if (currentScannedBuchung == null) return;

            int amount = checkInManager.clampAmount(currentScannedBuchung, checkInAmount);
            if (amount <= 0) {
                Toast.makeText(this, "Keine freien Plätze mehr.", Toast.LENGTH_SHORT).show();
                return;
            }

            performCheckInByBestellnummer(currentScannedBuchung.getBestellnummer(), amount);
            hideConfirmation();
            currentScannedBuchung = null;
        });
    }

    // =================== Dummy List ===================

    private void seedDummyList() {
        scannedItems.clear();
        scannedItems.add("Max Mustermann – 12345");
        scannedItems.add("Anna Huber – 23456");
        scannedItems.add("Peter Klein – 34567");
        scannedItems.add("Sophie Wagner – 45678");
        scannedItems.add("Lukas Mayer – 56789");
        scannedItems.add("Julia Bauer – 67890");
        scannedItems.add("Daniel Schmidt – 78901");
        scannedItems.add("Lisa Fischer – 89012");
        scannedItems.add("Martin Hofer – 90123");
        scannedItems.add("Clara Weiss – 01234");
        scannedAdapter.notifyDataSetChanged();
    }

    // =================== Kamera ===================

    private void toggleCamera() {
        if (isCameraOn) stopCamera();
        else startCameraSafe();
    }

    private void startCameraSafe() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }
        startCamera();
    }

    private void startCamera() {
        cameraController.start(
                this,
                previewView,
                CameraSelector.LENS_FACING_BACK,
                new Size(640, 480)
        );

        isCameraOn = true;
        isProcessing = false;
        setCameraUi(true);
        textResult.setText("Kamera aktiv – halte QR/Bestellnummer ins Bild");
    }

    private void stopCamera() {
        try {
            ImageAnalysis analysis = cameraController.getImageAnalysis();
            if (analysis != null) analysis.clearAnalyzer();
            cameraController.stop();
        } catch (Exception e) {
            Log.e(TAG, "stopCamera failed", e);
        }

        isCameraOn = false;
        isProcessing = false;
        setCameraUi(false);
        textResult.setText("Kamera manuell starten und dann scannen");
    }

    private void setCameraUi(boolean on) {
        // Preview komplett ausblendbar
        if (cameraCard != null) cameraCard.setVisibility(on ? View.VISIBLE : View.GONE);
        btnToggleCamera.setText(on ? "Kamera AUS" : "Kamera AN");
    }

    // Permission: NICHT auto-start, nur Info
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Berechtigung OK. Du kannst Kamera einschalten.", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Kamera-Berechtigung verweigert", Toast.LENGTH_LONG).show();
            }
        }
    }

    // =================== Analyzer OCR/QR (ALTE FUNKTION) ===================

    private void startOneShotOcr() {
        ImageAnalysis analysis = cameraController.getImageAnalysis();
        if (analysis == null) return;
        if (isProcessing) return;

        isProcessing = true;
        analysis.clearAnalyzer();

        analysis.setAnalyzer(analyzerExecutor, new OcrAnalyzer(new OcrAnalyzer.Callback() {
            @Override
            public void onOcrText(@NonNull String fullText) {
                final String order = NameExtractor.extractOrder(fullText);

                runOnUiThread(() -> {
                    if (order == null) {
                        textResult.setText("Keine Bestellnummer erkannt.");
                        Toast.makeText(AttendanceCheckInActivity.this, "Keine Bestellnummer im Text gefunden.", Toast.LENGTH_SHORT).show();
                        finishProcessing(analysis);
                        return;
                    }

                    Buchung found = matcher.findByBestellnummer(allBuchungen, order);
                    if (found == null) {
                        textResult.setText("Nicht gefunden: " + order);
                        hideConfirmation();
                        finishProcessing(analysis);
                        return;
                    }

                    showConfirmation(found);
                    finishProcessing(analysis);
                });
            }

            @Override
            public void onOcrError(@NonNull Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(AttendanceCheckInActivity.this, "OCR-Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finishProcessing(analysis);
                });
            }

            @Override
            public void onDone() {
                runOnUiThread(() -> finishProcessing(analysis));
            }
        }));
    }

    private void startOneShotQr() {
        ImageAnalysis analysis = cameraController.getImageAnalysis();
        if (analysis == null) return;
        if (isProcessing) return;

        isProcessing = true;
        analysis.clearAnalyzer();

        analysis.setAnalyzer(analyzerExecutor, new QrAnalyzer(new QrAnalyzer.Callback() {
            @Override
            public void onQrRaw(@NonNull String rawValue) {
                final String participantName = NameExtractor.extractQRName(rawValue);

                runOnUiThread(() -> {
                    if (participantName == null) {
                        textResult.setText("QR-Code hat falsches Format.");
                        Toast.makeText(AttendanceCheckInActivity.this, "QR-Code hat falsches Format.", Toast.LENGTH_LONG).show();
                        finishProcessing(analysis);
                        return;
                    }

                    Buchung found = matcher.findByDisplayName(allBuchungen, participantName);
                    if (found == null) {
                        textResult.setText("Nicht gefunden: " + participantName);
                        hideConfirmation();
                        finishProcessing(analysis);
                        return;
                    }

                    showConfirmation(found);
                    finishProcessing(analysis);
                });
            }

            @Override
            public void onQrEmpty() {
                runOnUiThread(() -> {
                    Toast.makeText(AttendanceCheckInActivity.this, "Kein QR-Code erkannt", Toast.LENGTH_SHORT).show();
                    finishProcessing(analysis);
                });
            }

            @Override
            public void onQrError(@NonNull Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(AttendanceCheckInActivity.this, "QR Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finishProcessing(analysis);
                });
            }

            @Override
            public void onDone() {
                runOnUiThread(() -> finishProcessing(analysis));
            }
        }));
    }

    private void finishProcessing(@NonNull ImageAnalysis analysis) {
        try { analysis.clearAnalyzer(); } catch (Exception ignored) {}
        isProcessing = false;
    }

    // =================== Confirmation Popup (ALTE FUNKTION) ===================

    private void showConfirmation(@NonNull Buchung buchung) {
        currentScannedBuchung = buchung;

        int free = checkInManager.freeSeats(buchung);
        if (free < 1) {
            hideConfirmation();
            textResult.setText("Keine freien Plätze mehr für diese Buchung.");
            Toast.makeText(this, "Keine freien Plätze mehr.", Toast.LENGTH_SHORT).show();
            return;
        }

        // oben im "Aktueller Scan"
        attendeeNameText.setText("Teilnehmer: " + safeStr(buchung.getDisplayName()));
        attendeeSeatsText.setText(
                "Bestellnr.: " + safeStr(buchung.getBestellnummer())
                        + " | Plätze: " + buchung.getAnzahlPlaetze()
                        + " | Eingecheckt: " + buchung.getCheckedInCount()
                        + " | Frei: " + free
        );

        // Popup
        checkInAmount = 1;
        updateCheckInAmountUI();

        confirmationTitleText.setText("Check-in bestätigen");
        confirmationSeatsText.setText(
                "Plätze: " + buchung.getAnzahlPlaetze()
                        + " | Eingecheckt: " + buchung.getCheckedInCount()
                        + " | Frei: " + free
        );

        confirmationLayout.setVisibility(View.VISIBLE);
        textResult.setText("Bitte Check-in bestätigen.");
    }

    private void hideConfirmation() {
        if (confirmationLayout != null) confirmationLayout.setVisibility(View.GONE);
    }

    private void setupPlusMinus() {
        btnMinus.setOnClickListener(v -> {
            if (checkInManager.canDecrease(checkInAmount)) {
                checkInAmount--;
                updateCheckInAmountUI();
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (currentScannedBuchung == null) return;

            if (checkInManager.canIncrease(currentScannedBuchung, checkInAmount)) {
                checkInAmount++;
                updateCheckInAmountUI();
            } else {
                Toast.makeText(this, "Nicht mehr freie Plätze verfügbar.", Toast.LENGTH_SHORT).show();
            }
        });

        updateCheckInAmountUI();
    }

    private void updateCheckInAmountUI() {
        checkInCountText.setText(String.valueOf(checkInAmount));
    }

    // =================== API / Data Load (ALTE FUNKTION) ===================

    private void loadBuchungenAndAnwesenheiten(Runnable onFinished) {
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Keine veranstaltung_id gesetzt.", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        apiService.getBuchungenByVeranstaltung(veranstaltungId)
                .enqueue(new retrofit2.Callback<List<Buchung>>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<List<Buchung>> call,
                                           @NonNull retrofit2.Response<List<Buchung>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            runOnUiThread(() -> Toast.makeText(AttendanceCheckInActivity.this,
                                    "Fehler beim Laden der Buchungen: " + response.code(),
                                    Toast.LENGTH_LONG).show());
                            return;
                        }

                        List<Buchung> buchungen = response.body();

                        apiService.getAnwesenheitenByVeranstaltung(veranstaltungId)
                                .enqueue(new retrofit2.Callback<List<Anwesenheit>>() {
                                    @Override
                                    public void onResponse(@NonNull retrofit2.Call<List<Anwesenheit>> call2,
                                                           @NonNull retrofit2.Response<List<Anwesenheit>> response2) {
                                        runOnUiThread(() -> {
                                            if (response2.isSuccessful() && response2.body() != null) {
                                                HashMap<Integer, Integer> sums = new HashMap<>();
                                                for (Anwesenheit a : response2.body()) {
                                                    int key = a.getBuchungId();
                                                    int cur = sums.containsKey(key) ? sums.get(key) : 0;
                                                    sums.put(key, cur + a.getAnzahlEingecheckt());
                                                }
                                                for (Buchung b : buchungen) {
                                                    int checked = sums.containsKey(b.getBuchungId()) ? sums.get(b.getBuchungId()) : 0;
                                                    b.setCheckedInCount(checked);
                                                }
                                            } else {
                                                for (Buchung b : buchungen) b.setCheckedInCount(0);
                                            }

                                            allBuchungen = buchungen;
                                            if (onFinished != null) onFinished.run();
                                        });
                                    }

                                    @Override
                                    public void onFailure(@NonNull retrofit2.Call<List<Anwesenheit>> call2,
                                                          @NonNull Throwable t) {
                                        runOnUiThread(() -> {
                                            for (Buchung b : buchungen) b.setCheckedInCount(0);
                                            allBuchungen = buchungen;
                                            if (onFinished != null) onFinished.run();
                                            Toast.makeText(AttendanceCheckInActivity.this,
                                                    "Anwesenheiten konnten nicht geladen werden (checkedIn=0).",
                                                    Toast.LENGTH_LONG).show();
                                        });
                                    }
                                });
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<List<Buchung>> call, @NonNull Throwable t) {
                        runOnUiThread(() -> Toast.makeText(AttendanceCheckInActivity.this,
                                "Netzwerkfehler beim Laden der Buchungen: " + t.getMessage(),
                                Toast.LENGTH_LONG).show());
                    }
                });
    }

    private void performCheckInByBestellnummer(String bestellnummer, int anzahl) {
        if (bestellnummer == null || bestellnummer.trim().isEmpty()) {
            Toast.makeText(this, "Keine Bestellnummer für Check-in gefunden", Toast.LENGTH_SHORT).show();
            return;
        }
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Keine veranstaltung_id gesetzt.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (anzahl < 1) anzahl = 1;

        final int finalAnzahl = anzahl;

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        CheckinByBestellnummerRequest body = new CheckinByBestellnummerRequest(bestellnummer, finalAnzahl);

        apiService.checkInByBestellnummer(ADMIN_TOKEN, veranstaltungId, body)
                .enqueue(new retrofit2.Callback<Anwesenheit>() {
                    @Override
                    public void onResponse(@NonNull retrofit2.Call<Anwesenheit> call,
                                           @NonNull retrofit2.Response<Anwesenheit> response) {
                        runOnUiThread(() -> {
                            if (response.isSuccessful()) {
                                String msg = "Check-in (" + finalAnzahl + ") für " + bestellnummer + " erfolgreich!";
                                Toast.makeText(AttendanceCheckInActivity.this, msg, Toast.LENGTH_LONG).show();
                                textResult.setText(msg);

                                // Liste updaten + nach Checkin neu laden
                                scannedItems.add(0, safeStr(currentScannedBuchung != null ? currentScannedBuchung.getDisplayName() : "")
                                        + " – " + bestellnummer + " (+ " + finalAnzahl + ")");
                                scannedAdapter.notifyDataSetChanged();

                                loadBuchungenAndAnwesenheiten(null);
                            } else {
                                String errorMessage = "API-Fehler beim Check-in. Code: " + response.code();
                                Toast.makeText(AttendanceCheckInActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                textResult.setText(errorMessage);
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<Anwesenheit> call, @NonNull Throwable t) {
                        runOnUiThread(() -> {
                            Toast.makeText(AttendanceCheckInActivity.this, "Netzwerkfehler: " + t.getMessage(), Toast.LENGTH_LONG).show();
                            textResult.setText("Check-in fehlgeschlagen.\nBitte Netzwerkverbindung prüfen.");
                        });
                    }
                });
    }

    private String safeStr(String s) {
        return (s == null) ? "" : s;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isCameraOn) stopCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { if (analyzerExecutor != null) analyzerExecutor.shutdown(); } catch (Exception ignored) {}
    }
}