package com.example.da_prototyp_ocr.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AttendanceCheckInActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 100;
    private static final String TAG = "AttendanceCheckIn";
    private static final String ADMIN_TOKEN = "supersecret-token";

    // ===== UI =====
    private PreviewView previewView;
    private View cameraCard;
    private View scannedCard;
    private TextView textResult;

    private Button scanBtn;
    private Button qrBtn;
    private Button btnToggleCamera;

    private ImageButton btnAddManual;
    private EditText searchField;
    private ListView teilnehmerList;
    private ParticipantAdapter teilnehmerAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;

    // Bottom overlay popup
    private View dimView;
    private LinearLayout confirmationLayout;

    // Popup texts
    private TextView confirmationTitleText;
    private TextView tvInfoBestellnr, tvInfoPlaetze, tvInfoEingecheckt, tvInfoFrei;

    private Button btnMinus, btnPlus;
    private TextView checkInCountText;
    private Button confirmCheckInBtn;

    // ===== State =====
    private int veranstaltungId = -1;
    private int checkInAmount = 1;
    private volatile boolean isProcessing = false;

    private List<Buchung> allBuchungen = new ArrayList<>();
    private List<Buchung> filteredBuchungen = new ArrayList<>();
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
        scannedCard = findViewById(R.id.scannedCard);
        textResult = findViewById(R.id.textResult);

        scanBtn = findViewById(R.id.scanBtn);
        qrBtn = findViewById(R.id.btn_qr);
        btnToggleCamera = findViewById(R.id.btnToggleCamera);

        btnAddManual = findViewById(R.id.btnAddManual);
        searchField = findViewById(R.id.searchField);
        teilnehmerList = findViewById(R.id.scannedList);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        teilnehmerAdapter = new ParticipantAdapter(this, filteredBuchungen);
        teilnehmerList.setAdapter(teilnehmerAdapter);

        dimView = findViewById(R.id.dimView);
        confirmationLayout = findViewById(R.id.confirmationLayout);

        confirmationTitleText = findViewById(R.id.confirmationTitleText);
        tvInfoBestellnr = findViewById(R.id.tvInfoBestellnr);
        tvInfoPlaetze = findViewById(R.id.tvInfoPlaetze);
        tvInfoEingecheckt = findViewById(R.id.tvInfoEingecheckt);
        tvInfoFrei = findViewById(R.id.tvInfoFrei);

        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);
        checkInCountText = findViewById(R.id.checkInCountText);
        confirmCheckInBtn = findViewById(R.id.confirmCheckInBtn);

        analyzerExecutor = Executors.newSingleThreadExecutor();

        // Inset padding (damit Button nicht unter System-Navigation verschwindet)
        applyBottomInsetPaddingToPopup();

        veranstaltungId = getIntent().getIntExtra("VERANSTALTUNG_ID", -1);
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Fehler: Keine veranstaltung_id übergeben.", Toast.LENGTH_LONG).show();
        }

        // Kamera startet NICHT automatisch
        setCameraUi(false);
        hideConfirmation();

        // Teilnehmerliste beim Start laden
        loadBuchungenAndAnwesenheiten(null);

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

        // Plus Button: Neue Buchung hinzufügen
        btnAddManual.setOnClickListener(v -> {
            // TODO: Dialog oder neue Activity zum Hinzufügen einer Buchung öffnen
            Toast.makeText(this, "Neue Buchung hinzufügen - Feature kommt bald", Toast.LENGTH_SHORT).show();
        });

        // Suchfunktion
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTeilnehmer(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Teilnehmer anklicken öffnet Confirmation Popup
        teilnehmerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Buchung selectedBuchung = filteredBuchungen.get(position);
                showConfirmation(selectedBuchung);
            }
        });

        // Pull-to-Refresh
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                loadBuchungenAndAnwesenheiten(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(AttendanceCheckInActivity.this, "Liste aktualisiert", Toast.LENGTH_SHORT).show();
                });
            });
        }

        // Dim klick schließt Popup
        if (dimView != null) dimView.setOnClickListener(v -> hideConfirmation());

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

    private void applyBottomInsetPaddingToPopup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && confirmationLayout != null) {
            confirmationLayout.setOnApplyWindowInsetsListener((v, insets) -> {
                int bottom = insets.getSystemWindowInsetBottom();
                v.setPadding(
                        v.getPaddingLeft(),
                        v.getPaddingTop(),
                        v.getPaddingRight(),
                        Math.max(v.getPaddingBottom(), bottom + dp(10))
                );
                return insets;
            });
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    // ===== Filter Funktion =====
    private void filterTeilnehmer(String query) {
        filteredBuchungen.clear();

        if (query == null || query.trim().isEmpty()) {
            filteredBuchungen.addAll(allBuchungen);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (Buchung buchung : allBuchungen) {
                String displayName = safeStr(buchung.getDisplayName()).toLowerCase();
                String bestellnr = safeStr(buchung.getBestellnummer()).toLowerCase();
                String vorname = safeStr(buchung.getVorname()).toLowerCase();
                String nachname = safeStr(buchung.getNachname()).toLowerCase();

                if (displayName.contains(lowerQuery) ||
                        bestellnr.contains(lowerQuery) ||
                        vorname.contains(lowerQuery) ||
                        nachname.contains(lowerQuery)) {
                    filteredBuchungen.add(buchung);
                }
            }
        }

        // Alphabetisch sortieren nach Namen
        Collections.sort(filteredBuchungen, new Comparator<Buchung>() {
            @Override
            public int compare(Buchung b1, Buchung b2) {
                String name1 = b1.getDisplayName();
                if (name1 == null || name1.trim().isEmpty()) {
                    name1 = b1.getVorname() + " " + b1.getNachname();
                }

                String name2 = b2.getDisplayName();
                if (name2 == null || name2.trim().isEmpty()) {
                    name2 = b2.getVorname() + " " + b2.getNachname();
                }

                return name1.compareToIgnoreCase(name2);
            }
        });

        teilnehmerAdapter.notifyDataSetChanged();
    }

    // ===== Kamera UI: wenn Kamera AN -> Gescannt-Kachel ausblenden =====
    private void setCameraUi(boolean on) {
        if (cameraCard != null) cameraCard.setVisibility(on ? View.VISIBLE : View.GONE);

        // weniger Ablenkung: Liste ausblenden wenn Kamera an
        if (scannedCard != null) scannedCard.setVisibility(on ? View.GONE : View.VISIBLE);

        btnToggleCamera.setText(on ? "Kamera AUS" : "Kamera AN");
        textResult.setText(on ? "Kamera aktiv – halte QR/Bestellnummer ins Bild"
                : "Kamera manuell starten und dann scannen");
    }

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
                new Size(720, 720) // quadratischer + größer
        );
        isCameraOn = true;
        isProcessing = false;
        setCameraUi(true);
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
        hideConfirmation();
        setCameraUi(false);
    }

    // Permission: NICHT auto-start
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

    // ===== Analyzer OCR =====
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
                        Toast.makeText(AttendanceCheckInActivity.this, "Keine Bestellnummer erkannt.", Toast.LENGTH_SHORT).show();
                        finishProcessing(analysis);
                        return;
                    }

                    Buchung found = matcher.findByBestellnummer(allBuchungen, order);
                    if (found == null) {
                        Toast.makeText(AttendanceCheckInActivity.this, "Nicht gefunden: " + order, Toast.LENGTH_SHORT).show();
                        hideConfirmation();
                        finishProcessing(analysis);
                        return;
                    }

                    // Kamera bleibt AN, Popup kommt unten als Overlay
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

    // ===== Analyzer QR =====
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
                        Toast.makeText(AttendanceCheckInActivity.this, "QR-Code hat falsches Format.", Toast.LENGTH_SHORT).show();
                        finishProcessing(analysis);
                        return;
                    }

                    Buchung found = matcher.findByDisplayName(allBuchungen, participantName);
                    if (found == null) {
                        Toast.makeText(AttendanceCheckInActivity.this, "Nicht gefunden: " + participantName, Toast.LENGTH_SHORT).show();
                        hideConfirmation();
                        finishProcessing(analysis);
                        return;
                    }

                    // Kamera bleibt AN, Popup kommt unten als Overlay
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

    // ===== Bottom Confirmation Overlay =====
    private void showConfirmation(@NonNull Buchung buchung) {
        currentScannedBuchung = buchung;

        int free = checkInManager.freeSeats(buchung);
        if (free < 1) {
            hideConfirmation();
            Toast.makeText(this, "Keine freien Plätze mehr.", Toast.LENGTH_SHORT).show();
            return;
        }

        checkInAmount = 1;
        updateCheckInAmountUI();

        // Titel groß
        confirmationTitleText.setText("Teilnehmer: " + safeStr(buchung.getDisplayName()));

        // Infos gestapelt
        tvInfoBestellnr.setText("Bestellnr.: " + safeStr(buchung.getBestellnummer()));
        tvInfoPlaetze.setText("Plätze: " + buchung.getAnzahlPlaetze());
        tvInfoEingecheckt.setText("Eingecheckt: " + buchung.getCheckedInCount());
        tvInfoFrei.setText("Frei: " + free);

        if (dimView != null) dimView.setVisibility(View.VISIBLE);
        if (confirmationLayout != null) confirmationLayout.setVisibility(View.VISIBLE);
    }

    private void hideConfirmation() {
        if (dimView != null) dimView.setVisibility(View.GONE);
        if (confirmationLayout != null) confirmationLayout.setVisibility(View.GONE);
    }

    private void setupPlusMinus() {
        // sicherstellen, dass +/− sichtbar sind
        btnMinus.setText("−");
        btnPlus.setText("+");

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

    // ===== API Load =====
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
                                            filterTeilnehmer(searchField.getText().toString());
                                            if (onFinished != null) onFinished.run();
                                        });
                                    }

                                    @Override
                                    public void onFailure(@NonNull retrofit2.Call<List<Anwesenheit>> call2,
                                                          @NonNull Throwable t) {
                                        runOnUiThread(() -> {
                                            for (Buchung b : buchungen) b.setCheckedInCount(0);
                                            allBuchungen = buchungen;
                                            filterTeilnehmer(searchField.getText().toString());
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

                                // Liste neu laden nach erfolgreichem Check-in
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