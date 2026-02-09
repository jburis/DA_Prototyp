package com.example.da_prototyp_ocr.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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

    private PreviewView previewView;
    private Button scanBtn, qrBtn;
    private TextView textResult;

    // Confirmation UI
    private LinearLayout confirmationLayout;
    private TextView attendeeNameText, attendeeSeatsText;
    private Button btnMinus, btnPlus;
    private TextView checkInCountText;
    private Button confirmCheckInBtn;

    // Toggle Button (muss im XML existieren: @+id/btnToggleCamera)
    private Button toggleCameraBtn;

    // State
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

        // Views
        textResult = findViewById(R.id.textResult);
        previewView = findViewById(R.id.previewView);
        scanBtn = findViewById(R.id.scanBtn);
        qrBtn = findViewById(R.id.btn_qr);

        confirmationLayout = findViewById(R.id.confirmationLayout);
        attendeeNameText = findViewById(R.id.attendeeNameText);
        attendeeSeatsText = findViewById(R.id.attendeeSeatsText);
        confirmCheckInBtn = findViewById(R.id.confirmCheckInBtn);

        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);
        checkInCountText = findViewById(R.id.checkInCountText);

        toggleCameraBtn = findViewById(R.id.btnToggleCamera);

        confirmationLayout.setVisibility(View.GONE);

        analyzerExecutor = Executors.newSingleThreadExecutor();

        veranstaltungId = getIntent().getIntExtra("VERANSTALTUNG_ID", -1);
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Fehler: Keine veranstaltung_id übergeben.", Toast.LENGTH_LONG).show();
        }

        setupPlusMinus();

        // Toggle Kamera
        if (toggleCameraBtn != null) {
            toggleCameraBtn.setOnClickListener(v -> toggleCamera());
        }
        updateToggleButtonUi();

        // Camera permission + auto-start
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            startCamera();
        }

        scanBtn.setOnClickListener(v -> {
            confirmationLayout.setVisibility(View.GONE);
            if (!isCameraOn) {
                Toast.makeText(this, "Kamera ist aus. Bitte Kamera einschalten.", Toast.LENGTH_SHORT).show();
                return;
            }
            loadBuchungenAndAnwesenheiten(this::startOneShotOcr);
        });

        qrBtn.setOnClickListener(v -> {
            confirmationLayout.setVisibility(View.GONE);
            if (!isCameraOn) {
                Toast.makeText(this, "Kamera ist aus. Bitte Kamera einschalten.", Toast.LENGTH_SHORT).show();
                return;
            }
            loadBuchungenAndAnwesenheiten(this::startOneShotQr);
        });

        confirmCheckInBtn.setOnClickListener(v -> {
            if (currentScannedBuchung != null) {
                int amount = checkInManager.clampAmount(currentScannedBuchung, checkInAmount);
                if (amount <= 0) {
                    Toast.makeText(this, "Keine freien Plätze mehr.", Toast.LENGTH_SHORT).show();
                    return;
                }
                performCheckInByBestellnummer(currentScannedBuchung.getBestellnummer(), amount);
                confirmationLayout.setVisibility(View.GONE);
                currentScannedBuchung = null;
            }
        });
    }

    // =================== Kamera Toggle ===================

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

    private void stopCamera() {
        try {
            ImageAnalysis analysis = cameraController.getImageAnalysis();
            if (analysis != null) analysis.clearAnalyzer();

            cameraController.stop();

            isCameraOn = false;
            isProcessing = false;

            // Optional: Preview “optisch” aus
            previewView.setVisibility(View.INVISIBLE);

            updateToggleButtonUi();
            Toast.makeText(this, "Kamera aus", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "stopCamera failed", e);
        }
    }

    private void updateToggleButtonUi() {
        if (toggleCameraBtn == null) return;
        toggleCameraBtn.setText(isCameraOn ? "Kamera AUS" : "Kamera AN");
    }

    // =================== Kamera Start + Analyzer ===================

    private void startCamera() {
        cameraController.start(
                this,
                previewView,
                CameraSelector.LENS_FACING_BACK,
                new Size(640, 480)
        );

        previewView.setVisibility(View.VISIBLE);
        isCameraOn = true;
        updateToggleButtonUi();
    }

    private void startOneShotOcr() {
        ImageAnalysis analysis = cameraController.getImageAnalysis();
        if (analysis == null) return;
        if (isProcessing) return;

        isProcessing = true;

        analysis.clearAnalyzer();
        analysis.setAnalyzer(analyzerExecutor, new OcrAnalyzer(new OcrAnalyzer.Callback() {
            @Override
            public void onOcrText(@NonNull String fullText) {
                String order = NameExtractor.extractOrder(fullText);

                runOnUiThread(() -> {
                    if (order == null) {
                        textResult.setText("Keine Bestellnummer erkannt.\n\n" + fullText);
                        Toast.makeText(AttendanceCheckInActivity.this, "Keine Bestellnummer im Text gefunden.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Buchung found = matcher.findByBestellnummer(allBuchungen, order);
                    if (found == null) {
                        textResult.setText("Fehler: Bestellnummer '" + order + "' nicht in der Liste gefunden.");
                        confirmationLayout.setVisibility(View.GONE);
                    } else {
                        showConfirmation(found);
                    }
                });
            }

            @Override
            public void onOcrError(@NonNull Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(AttendanceCheckInActivity.this, "OCR-Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onDone() {
                isProcessing = false;
                analysis.clearAnalyzer();
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
                String participantName = NameExtractor.extractQRName(rawValue);

                runOnUiThread(() -> {
                    if (participantName == null) {
                        textResult.setText("QR-Code hat nicht das erwartete Format.\n\n" + rawValue);
                        Toast.makeText(AttendanceCheckInActivity.this, "QR-Code hat falsches Format.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    Buchung found = matcher.findByDisplayName(allBuchungen, participantName);
                    if (found == null) {
                        textResult.setText("Fehler: Teilnehmer '" + participantName + "' nicht in der Liste gefunden.");
                        confirmationLayout.setVisibility(View.GONE);
                    } else {
                        showConfirmation(found);
                    }
                });
            }

            @Override
            public void onQrEmpty() {
                runOnUiThread(() ->
                        Toast.makeText(AttendanceCheckInActivity.this, "Kein QR-Code erkannt", Toast.LENGTH_SHORT).show()
                );
            }

            @Override
            public void onQrError(@NonNull Exception e) {
                Log.e(TAG, "QR Analyse fehlgeschlagen", e);
            }

            @Override
            public void onDone() {
                isProcessing = false;
                analysis.clearAnalyzer();
            }
        }));
    }

    private void showConfirmation(Buchung buchung) {
        currentScannedBuchung = buchung;

        int free = checkInManager.freeSeats(buchung);
        if (free < 1) {
            confirmationLayout.setVisibility(View.GONE);
            textResult.setText("Keine freien Plätze mehr für diese Buchung.");
            Toast.makeText(this, "Keine freien Plätze mehr.", Toast.LENGTH_SHORT).show();
            return;
        }

        checkInAmount = 1;
        updateCheckInAmountUI();

        attendeeNameText.setText("Teilnehmer: " + buchung.getDisplayName());
        attendeeSeatsText.setText(
                "Plätze: " + buchung.getAnzahlPlaetze()
                        + " / Eingecheckt: " + buchung.getCheckedInCount()
                        + " / Frei: " + free
        );

        confirmationLayout.setVisibility(View.VISIBLE);
        textResult.setText("Bitte Check-in bestätigen.");
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

    @Override
    protected void onStop() {
        super.onStop();
        stopCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (analyzerExecutor != null) analyzerExecutor.shutdown();
        } catch (Exception ignored) {}
    }

    // ✅ FIX: richtige Signatur! (grantResults ist int[])
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else if (requestCode == REQ_CAMERA) {
            Toast.makeText(this, "Kamera-Berechtigung verweigert", Toast.LENGTH_LONG).show();
        }
    }

    // =================== API / Data Load ===================

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
                    public void onFailure(@NonNull retrofit2.Call<List<Buchung>> call,
                                          @NonNull Throwable t) {
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
                                String msg = "Check-in (" + finalAnzahl + ") für Bestellnummer " + bestellnummer + " erfolgreich!";
                                Toast.makeText(AttendanceCheckInActivity.this, msg, Toast.LENGTH_LONG).show();
                                textResult.setText(msg);
                                loadBuchungenAndAnwesenheiten(null);
                            } else {
                                String errorMessage = "API-Fehler beim Check-in. Code: " + response.code();
                                Toast.makeText(AttendanceCheckInActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                textResult.setText(errorMessage);
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<Anwesenheit> call,
                                          @NonNull Throwable t) {
                        runOnUiThread(() -> {
                            String failureMessage = "Netzwerkfehler: " + t.getMessage();
                            Toast.makeText(AttendanceCheckInActivity.this, failureMessage, Toast.LENGTH_LONG).show();
                            textResult.setText("Check-in fehlgeschlagen.\nBitte Netzwerkverbindung prüfen.");
                        });
                    }
                });
    }
}