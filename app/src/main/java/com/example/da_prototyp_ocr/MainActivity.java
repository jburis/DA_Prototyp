package com.example.da_prototyp_ocr;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class MainActivity extends AppCompatActivity {
    private static final int REQ_CAMERA = 100;
    private PreviewView previewView;
    private Button scanBtn, qrBtn;
    private ExecutorService cameraExecutor;
    private ImageAnalysis analysis;

    private TextView textResult;
    private boolean scanQrMode = false;

    // --- UI & State Variablen ---
    private LinearLayout confirmationLayout;
    private TextView attendeeNameText, attendeeSeatsText;
    private Button confirmCheckInBtn;
    // statt List<Attendee>
    private List<Buchung> allBuchungen = new ArrayList<>();
    private Buchung currentScannedBuchung;

    // Die aktuell ausgewählte Veranstaltung (Liste)
    private int veranstaltungId = -1;

    // Admin Token (muss dem .env entsprechen)
    private static final String ADMIN_TOKEN = "supersecret-token";
    private Attendee currentScannedAttendee;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // --- Initialisierungen ---
        textResult = findViewById(R.id.textResult);
        previewView = findViewById(R.id.previewView);
        scanBtn = findViewById(R.id.scanBtn);
        qrBtn = findViewById(R.id.btn_qr);
        cameraExecutor = Executors.newSingleThreadExecutor();

        confirmationLayout = findViewById(R.id.confirmationLayout);
        attendeeNameText = findViewById(R.id.attendeeNameText);
        attendeeSeatsText = findViewById(R.id.attendeeSeatsText);
        confirmCheckInBtn = findViewById(R.id.confirmCheckInBtn);

        confirmationLayout.setVisibility(View.GONE);

        veranstaltungId = getIntent().getIntExtra("VERANSTALTUNG_ID", -1);
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Fehler: Keine veranstaltung_id übergeben.", Toast.LENGTH_LONG).show();
        }


        // Kamera-Berechtigung
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
        } else {
            startCamera();
        }

        // --- KORRIGIERTE CLICK-LISTENER ---
        scanBtn.setOnClickListener(v -> {
            confirmationLayout.setVisibility(View.GONE);
            loadBuchungenAndAnwesenheiten(() -> {
                scanQrMode = false;
                analyzeOnce();
            });
        });

        qrBtn.setOnClickListener(v -> {
            confirmationLayout.setVisibility(View.GONE);
            loadBuchungenAndAnwesenheiten(() -> {
                scanQrMode = true;
                analyzeOnce();
            });
        });


        confirmCheckInBtn.setOnClickListener(v -> {
            if (currentScannedBuchung != null) {
                performCheckInByBestellnummer(currentScannedBuchung.getBestellnummer());
                confirmationLayout.setVisibility(View.GONE);
                currentScannedBuchung = null;
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
                provider.unbindAll();
                provider.bindToLifecycle(this, selector, preview, analysis);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Kamera-Start fehlgeschlagen", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeOnce() {
        if (analysis == null) return;
        analysis.setAnalyzer(cameraExecutor, imageProxy -> {
            if (scanQrMode) {
                runQrScan(imageProxy);
            } else {
                runOcrOnFrame(imageProxy);
            }
            analysis.clearAnalyzer();
        });
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void runOcrOnFrame(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }
        InputImage img = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(img)
                .addOnSuccessListener(this::handleResult)
                .addOnFailureListener(e -> Toast.makeText(this, "OCR-Fehler: " + e.getMessage(), Toast.LENGTH_SHORT).show())
                .addOnCompleteListener(t -> imageProxy.close());
    }

    private void handleResult(Text visionText) {
        String full = visionText.getText();
        String order = NameExtractor.extractOrder(full);

        if (order != null) {
            searchBuchungLocally(order);
        } else {
            String name = NameExtractor.extractName(full);
            if (name != null) {
                performCheckInByName(name);
            } else {
                runOnUiThread(() -> {
                    textResult.setText("Keine Bestellnummer oder Name erkannt.\n\n" + full);
                    Toast.makeText(this, "Keine relevanten Daten im Text gefunden.", Toast.LENGTH_SHORT).show();
                });
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void runQrScan(ImageProxy imageProxy) {
        Image img = imageProxy.getImage();
        if (img == null) {
            imageProxy.close();
            return;
        }
        InputImage inputImage = InputImage.fromMediaImage(img, imageProxy.getImageInfo().getRotationDegrees());
        BarcodeScanner scanner = BarcodeScanning.getClient();
        scanner.process(inputImage)
                .addOnSuccessListener(barcodes -> {
                    if (!barcodes.isEmpty()) {
                        String qrCodeValue = barcodes.get(0).getRawValue();
                        Log.d("QRSCAN", "QR-Code Rohdaten: " + qrCodeValue);
                        String participantName = NameExtractor.extractQRName(qrCodeValue);
                        if (participantName != null) {
                            Log.d("QRSCAN", "Extrahierter Teilnehmer aus QR-Code: " + participantName);
                            performCheckInByName(participantName);
                        } else {
                            runOnUiThread(() -> {
                                String feedback = "QR-Code hat nicht das erwartete Format.";
                                if (qrCodeValue != null) feedback += "\nInhalt: " + qrCodeValue;
                                textResult.setText(feedback);
                                Toast.makeText(this, "QR-Code hat falsches Format.", Toast.LENGTH_LONG).show();
                            });
                        }
                    } else {
                        Log.d("QRSCAN", "Kein QR-Code im Bild gefunden.");
                        runOnUiThread(() -> Toast.makeText(this, "Kein QR-Code erkannt", Toast.LENGTH_SHORT).show());
                    }
                })
                .addOnFailureListener(e -> Log.e("QRSCAN", "QR-Code-Analyse fehlgeschlagen", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        if (code == REQ_CAMERA && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) startCamera();
    }

    private void loadBuchungenAndAnwesenheiten(Runnable onFinished) {
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Keine veranstaltung_id gesetzt.", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        apiService.getBuchungenByVeranstaltung(veranstaltungId)
                .enqueue(new retrofit2.Callback<List<Buchung>>() {
                    @Override
                    public void onResponse(@androidx.annotation.NonNull retrofit2.Call<List<Buchung>> call,
                                           @androidx.annotation.NonNull retrofit2.Response<List<Buchung>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                    "Fehler beim Laden der Buchungen: " + response.code(),
                                    Toast.LENGTH_LONG).show());
                            return;
                        }

                        List<Buchung> buchungen = response.body();

                        // Jetzt Anwesenheiten laden und pro Buchung_id summieren
                        apiService.getAnwesenheitenByVeranstaltung(veranstaltungId)
                                .enqueue(new retrofit2.Callback<List<Anwesenheit>>() {
                                    @Override
                                    public void onResponse(@androidx.annotation.NonNull retrofit2.Call<List<Anwesenheit>> call2,
                                                           @androidx.annotation.NonNull retrofit2.Response<List<Anwesenheit>> response2) {
                                        runOnUiThread(() -> {
                                            if (response2.isSuccessful() && response2.body() != null) {
                                                java.util.HashMap<Integer, Integer> sums = new java.util.HashMap<>();
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
                                                // Wenn Anwesenheiten nicht gehen, setzen wir checkedInCount = 0 (keine Logikänderung am Scan)
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
                                    public void onFailure(@androidx.annotation.NonNull retrofit2.Call<List<Anwesenheit>> call2,
                                                          @androidx.annotation.NonNull Throwable t) {
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
                    public void onFailure(@androidx.annotation.NonNull retrofit2.Call<List<Buchung>> call,
                                          @androidx.annotation.NonNull Throwable t) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                "Netzwerkfehler beim Laden der Buchungen: " + t.getMessage(),
                                Toast.LENGTH_LONG).show());
                    }
                });
    }


    private void searchBuchungLocally(String bestellnummer) {
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
            currentScannedBuchung = found;

            attendeeNameText.setText("Teilnehmer: " + currentScannedBuchung.getDisplayName());
            attendeeSeatsText.setText("Plätze: " + currentScannedBuchung.getAnzahlPlaetze()
                    + " / Eingecheckt: " + currentScannedBuchung.getCheckedInCount());

            confirmationLayout.setVisibility(View.VISIBLE);
            textResult.setText("Bitte bestätigen Sie den Check-in.");
        } else {
            textResult.setText("Fehler: Bestellnummer '" + bestellnummer + "' nicht in der Liste gefunden.");
            confirmationLayout.setVisibility(View.GONE);
        }
    }



    private void performCheckInByBestellnummer(String bestellnummer) {
        if (bestellnummer == null || bestellnummer.trim().isEmpty()) {
            Toast.makeText(this, "Keine Bestellnummer für Check-in gefunden", Toast.LENGTH_SHORT).show();
            return;
        }
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Keine veranstaltung_id gesetzt.", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        CheckinByBestellnummerRequest body = new CheckinByBestellnummerRequest(bestellnummer, 1);

        apiService.checkInByBestellnummer(ADMIN_TOKEN, veranstaltungId, body)
                .enqueue(new retrofit2.Callback<Anwesenheit>() {
                    @Override
                    public void onResponse(@androidx.annotation.NonNull retrofit2.Call<Anwesenheit> call,
                                           @androidx.annotation.NonNull retrofit2.Response<Anwesenheit> response) {
                        runOnUiThread(() -> {
                            if (response.isSuccessful()) {
                                String msg = "Check-in für Bestellnummer " + bestellnummer + " erfolgreich!";
                                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                                textResult.setText(msg);

                                // Refresh: Buchungen + Anwesenheiten neu laden, damit checkedInCount stimmt
                                loadBuchungenAndAnwesenheiten(null);
                            } else {
                                String errorMessage = "API-Fehler beim Check-in. Code: " + response.code();
                                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                                textResult.setText(errorMessage);
                            }
                        });
                    }

                    @Override
                    public void onFailure(@androidx.annotation.NonNull retrofit2.Call<Anwesenheit> call,
                                          @androidx.annotation.NonNull Throwable t) {
                        runOnUiThread(() -> {
                            String failureMessage = "Netzwerkfehler: " + t.getMessage();
                            Toast.makeText(MainActivity.this, failureMessage, Toast.LENGTH_LONG).show();
                            textResult.setText("Check-in fehlgeschlagen.\nBitte Netzwerkverbindung prüfen.");
                        });
                    }
                });
    }



    private void performCheckInByName(String participantName) {
        if (participantName == null || participantName.trim().isEmpty()) {
            Toast.makeText(this, "Kein Name für Check-in gefunden", Toast.LENGTH_SHORT).show();
            return;
        }
        if (veranstaltungId == -1) {
            Toast.makeText(this, "Keine veranstaltung_id gesetzt.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Minimaler Split: letzter Token = Nachname, Rest = Vorname
        String[] parts = participantName.trim().split("\\s+");
        if (parts.length < 2) {
            Toast.makeText(this, "Name nicht vollständig erkannt (Vorname + Nachname).", Toast.LENGTH_SHORT).show();
            return;
        }
        String nachname = parts[parts.length - 1];
        StringBuilder vornameSb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (i > 0) vornameSb.append(" ");
            vornameSb.append(parts[i]);
        }
        String vorname = vornameSb.toString();

        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        CheckinByNameRequest body = new CheckinByNameRequest(vorname, nachname, 1);

        apiService.checkInByName(ADMIN_TOKEN, veranstaltungId, body)
                .enqueue(new retrofit2.Callback<CheckinByNameResponse>() {
                    @Override
                    public void onResponse(@androidx.annotation.NonNull retrofit2.Call<CheckinByNameResponse> call,
                                           @androidx.annotation.NonNull retrofit2.Response<CheckinByNameResponse> response) {
                        runOnUiThread(() -> {
                            if (response.isSuccessful()) {
                                String msg = "Check-in für " + participantName + " erfolgreich!";
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
                    public void onFailure(@androidx.annotation.NonNull retrofit2.Call<CheckinByNameResponse> call,
                                          @androidx.annotation.NonNull Throwable t) {
                        runOnUiThread(() -> {
                            String failureMessage = "Netzwerkfehler: " + t.getMessage();
                            Toast.makeText(MainActivity.this, failureMessage, Toast.LENGTH_LONG).show();
                            textResult.setText("Check-in fehlgeschlagen.\nBitte Netzwerkverbindung prüfen.");
                        });
                    }
                });
    }
}
