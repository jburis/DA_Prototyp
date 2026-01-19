package com.example.da_prototyp_ocr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_CAMERA = 100;
    private static final String TAG = "MainActivity";
    private static final String ADMIN_TOKEN = "supersecret-token";

    private PreviewView previewView;
    private Button scanBtn, qrBtn;

    private ExecutorService cameraExecutor;
    private ImageAnalysis analysis;
    private ProcessCameraProvider cameraProvider;

    private TextView textResult;
    private boolean scanQrMode = false;

    // --- Confirmation UI (Panel) ---
    private LinearLayout confirmationLayout;
    private TextView attendeeNameText, attendeeSeatsText;

    // PLUS/MINUS UI (müssen im XML existieren!)
    private Button btnMinus, btnPlus;
    private TextView checkInCountText;

    private Button confirmCheckInBtn;

    // --- State ---
    private int checkInAmount = 1;
    private volatile boolean isProcessing = false; // Scan-Lock

    private List<Buchung> allBuchungen = new ArrayList<>();
    private Buchung currentScannedBuchung;

    private int veranstaltungId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Views ---
        textResult = findViewById(R.id.textResult);
        previewView = findViewById(R.id.previewView);
        scanBtn = findViewById(R.id.scanBtn);
        qrBtn = findViewById(R.id.btn_qr);

        confirmationLayout = findViewById(R.id.confirmationLayout);
        attendeeNameText = findViewById(R.id.attendeeNameText);
        attendeeSeatsText = findViewById(R.id.attendeeSeatsText);
        confirmCheckInBtn = findViewById(R.id.confirmCheckInBtn);

        // Diese IDs müssen in activity_main.xml vorhanden sein:
        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);
        checkInCountText = findViewById(R.id.checkInCountText);

        confirmationLayout.setVisibility(View.GONE);

        cameraExecutor = Executors.newSingleThreadExecutor();

        // Veranstaltung ID
        veranstaltungId = getIntent().getIntExtra("VERANSTALTUNG_ID", -1);
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Fehler: Keine veranstaltung_id übergeben.", Toast.LENGTH_LONG).show();
        }

        // Setup +/- Buttons
        setupPlusMinus();

        // Camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            startCamera();
        }

        // Scan Buchungsnummer (OCR)
        scanBtn.setOnClickListener(v -> {
            confirmationLayout.setVisibility(View.GONE);
            loadBuchungenAndAnwesenheiten(() -> {
                scanQrMode = false;
                analyzeOnce();
            });
        });

        // Scan QR
        qrBtn.setOnClickListener(v -> {
            confirmationLayout.setVisibility(View.GONE);
            loadBuchungenAndAnwesenheiten(() -> {
                scanQrMode = true;
                analyzeOnce();
            });
        });

        // Confirm button -> Bestellnummer + anzahl
        confirmCheckInBtn.setOnClickListener(v -> {
            if (currentScannedBuchung != null) {
                performCheckInByBestellnummer(currentScannedBuchung.getBestellnummer(), checkInAmount);
                confirmationLayout.setVisibility(View.GONE);
                currentScannedBuchung = null;
            }
        });
    }

    // ---------------- CameraX ----------------

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();

                Preview preview = new Preview.Builder()
                        // Emulator stabiler + weniger ruckeln
                        .setTargetResolution(new android.util.Size(640, 480))
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new android.util.Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                        .build();

                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, analysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "startCamera failed", e);
                Toast.makeText(this, "Kamera-Start fehlgeschlagen", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeOnce() {
        if (analysis == null) return;
        if (isProcessing) return;

        isProcessing = true;

        analysis.clearAnalyzer();
        analysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (scanQrMode) runQrScan(imageProxy);
            else runOcrOnFrame(imageProxy);
            analysis.clearAnalyzer();
        });
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void runOcrOnFrame(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            isProcessing = false;
            imageProxy.close();
            return;
        }

        InputImage img = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(img)
                .addOnSuccessListener(this::handleOcrResult)
                .addOnFailureListener(e -> Toast.makeText(this, "OCR-Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show())
                .addOnCompleteListener(t -> {
                    isProcessing = false;
                    imageProxy.close();
                });
    }

    private void handleOcrResult(Text visionText) {
        String full = visionText.getText();
        String order = NameExtractor.extractOrder(full);

        if (order != null) {
            runOnUiThread(() -> searchBuchungLocallyByBestellnummer(order));
        } else {
            runOnUiThread(() -> {
                textResult.setText("Keine Bestellnummer erkannt.\n\n" + full);
                Toast.makeText(this, "Keine Bestellnummer im Text gefunden.", Toast.LENGTH_SHORT).show();
            });
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void runQrScan(ImageProxy imageProxy) {
        Image img = imageProxy.getImage();
        if (img == null) {
            isProcessing = false;
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(img, imageProxy.getImageInfo().getRotationDegrees());
        BarcodeScanner scanner = BarcodeScanning.getClient();

        scanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty()) {
                        String qrCodeValue = barcodes.get(0).getRawValue();
                        Log.d(TAG, "QR-Code Rohdaten: " + qrCodeValue);

                        String participantName = NameExtractor.extractQRName(qrCodeValue);

                        if (participantName != null) {
                            runOnUiThread(() -> searchBuchungLocallyByName(participantName));
                        } else {
                            runOnUiThread(() -> {
                                String feedback = "QR-Code hat nicht das erwartete Format.";
                                if (qrCodeValue != null) feedback += "\nInhalt: " + qrCodeValue;
                                textResult.setText(feedback);
                                Toast.makeText(this, "QR-Code hat falsches Format.", Toast.LENGTH_LONG).show();
                            });
                        }
                    } else {
                        runOnUiThread(() -> Toast.makeText(this, "Kein QR-Code erkannt", Toast.LENGTH_SHORT).show());
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "QR-Code-Analyse fehlgeschlagen", e))
                .addOnCompleteListener(task -> {
                    isProcessing = false;
                    imageProxy.close();
                });
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_CAMERA && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // sauber freigeben -> reduziert "too many clients" / reopen loops
        try {
            if (cameraProvider != null) cameraProvider.unbindAll();
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (cameraExecutor != null) cameraExecutor.shutdown();
        } catch (Exception ignored) {}
    }

    // ---------------- Buchungen + Anwesenheiten laden ----------------

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
                            runOnUiThread(() -> Toast.makeText(MainActivity.this,
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
                                            else Toast.makeText(MainActivity.this,
                                                    allBuchungen.size() + " Buchungen geladen.",
                                                    Toast.LENGTH_SHORT).show();
                                        });
                                    }

                                    @Override
                                    public void onFailure(@NonNull retrofit2.Call<List<Anwesenheit>> call2,
                                                          @NonNull Throwable t) {
                                        runOnUiThread(() -> {
                                            for (Buchung b : buchungen) b.setCheckedInCount(0);
                                            allBuchungen = buchungen;
                                            if (onFinished != null) onFinished.run();
                                            Toast.makeText(MainActivity.this,
                                                    "Anwesenheiten konnten nicht geladen werden (checkedIn=0).",
                                                    Toast.LENGTH_LONG).show();
                                        });
                                    }
                                });
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<List<Buchung>> call,
                                          @NonNull Throwable t) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                "Netzwerkfehler beim Laden der Buchungen: " + t.getMessage(),
                                Toast.LENGTH_LONG).show());
                    }
                });
    }

    // ---------------- Local Search -> öffnet das Popup ----------------

    private void searchBuchungLocallyByBestellnummer(String bestellnummer) {
        if (bestellnummer == null || bestellnummer.trim().isEmpty()) {
            Toast.makeText(this, "Keine Bestellnummer gefunden.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Keine veranstaltung_id gesetzt.", Toast.LENGTH_SHORT).show();
            return;
        }

        Buchung found = null;
        for (Buchung b : allBuchungen) {
            if (bestellnummer.equals(b.getBestellnummer())) {
                found = b;
                break;
            }
        }

        if (found != null) {
            showConfirmation(found);
        } else {
            textResult.setText("Fehler: Bestellnummer '" + bestellnummer + "' nicht in der Liste gefunden.");
            confirmationLayout.setVisibility(View.GONE);
        }
    }

    private void searchBuchungLocallyByName(String participantName) {
        if (participantName == null || participantName.trim().isEmpty()) {
            Toast.makeText(this, "Kein Name im QR erkannt.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Keine veranstaltung_id gesetzt.", Toast.LENGTH_SHORT).show();
            return;
        }

        Buchung found = null;
        for (Buchung b : allBuchungen) {
            String display = b.getDisplayName();
            if (display != null && participantName.equalsIgnoreCase(display.trim())) {
                found = b;
                break;
            }
        }

        if (found != null) {
            showConfirmation(found);
        } else {
            textResult.setText("Fehler: Teilnehmer '" + participantName + "' nicht in der Liste gefunden.");
            confirmationLayout.setVisibility(View.GONE);
        }
    }

    private void showConfirmation(Buchung buchung) {
        currentScannedBuchung = buchung;

        // Default = 1, aber max = freie Plätze
        int free = buchung.getAnzahlPlaetze() - buchung.getCheckedInCount();
        if (free < 1) {
            confirmationLayout.setVisibility(View.GONE);
            textResult.setText("Keine freien Plätze mehr für diese Buchung.");
            Toast.makeText(this, "Keine freien Plätze mehr.", Toast.LENGTH_SHORT).show();
            return;
        }

        checkInAmount = 1;
        updateCheckInAmountUI();

        attendeeNameText.setText("Teilnehmer: " + buchung.getDisplayName());
        attendeeSeatsText.setText("Plätze: " + buchung.getAnzahlPlaetze()
                + " / Eingecheckt: " + buchung.getCheckedInCount()
                + " / Frei: " + free);

        confirmationLayout.setVisibility(View.VISIBLE);
        textResult.setText("Bitte Check-in bestätigen.");
    }

    // ---------------- Plus/Minus ----------------

    private void setupPlusMinus() {
        if (btnMinus == null || btnPlus == null || checkInCountText == null) return;

        btnMinus.setOnClickListener(v -> {
            if (checkInAmount > 1) {
                checkInAmount--;
                updateCheckInAmountUI();
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (currentScannedBuchung == null) return;

            int free = currentScannedBuchung.getAnzahlPlaetze() - currentScannedBuchung.getCheckedInCount();
            if (free < 1) {
                Toast.makeText(this, "Keine freien Plätze mehr.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (checkInAmount < free) {
                checkInAmount++;
                updateCheckInAmountUI();
            } else {
                Toast.makeText(this, "Nicht mehr freie Plätze verfügbar.", Toast.LENGTH_SHORT).show();
            }
        });

        updateCheckInAmountUI();
    }

    private void updateCheckInAmountUI() {
        if (checkInCountText != null) {
            checkInCountText.setText(String.valueOf(checkInAmount));
        }
    }

    // ---------------- API Check-in ----------------

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

        // Lambda braucht final/effectively final
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
                                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                                textResult.setText(msg);

                                loadBuchungenAndAnwesenheiten(null);
                            } else {
                                String errorMessage = "API-Fehler beim Check-in. Code: " + response.code();
                                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                textResult.setText(errorMessage);
                            }
                        });
                    }

                    @Override
                    public void onFailure(@NonNull retrofit2.Call<Anwesenheit> call,
                                          @NonNull Throwable t) {
                        runOnUiThread(() -> {
                            String failureMessage = "Netzwerkfehler: " + t.getMessage();
                            Toast.makeText(MainActivity.this, failureMessage, Toast.LENGTH_LONG).show();
                            textResult.setText("Check-in fehlgeschlagen.\nBitte Netzwerkverbindung prüfen.");
                        });
                    }
                });
    }
}