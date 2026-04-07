# KWP Check-In App

Android-App zur digitalen Anwesenheitserfassung für das Kuratorium Wiener Pensionisten-Wohnhäuser (KWP).

## Überblick

Die App ermöglicht Klubbetreuern das Erfassen von Anwesenheiten bei Veranstaltungen durch:
- **PDF-Import:** Automatisches Einlesen von Teilnehmerlisten aus PDF-Dokumenten
- **Texterkennung:** Check-In durch Scannen von Buchungsbestätigungen (OCR) oder Klubkarten (QR-Code)
- **Live-Synchronisation:** Echtzeit-Abgleich mit dem Backend für mehrere Geräte

## Tech Stack

| Komponente | Technologie | Version |
|------------|-------------|---------|
| Sprache | Java | 11 |
| IDE | Android Studio | Narwhal 2025.1.3 |
| Min SDK | Android | 7.0 (API 24) |
| PDF-Verarbeitung | Apache PDFBox Android | 2.0.27.0 |
| Texterkennung | Google ML Kit | 16.0.0 |
| QR-Scanning | ML Kit Barcode | 17.0.0 |
| Kamera | CameraX | 1.3.0 |
| HTTP-Client | Retrofit | 2.9.0 |
| JSON | Gson | 2.9.0 |

## Projektstruktur

```
com.example.da_prototyp_ocr/
├── ui/                 # Activities und Adapter
├── camera/             # Kamerasteuerung und Analyzer
├── logic/              # Business-Logik (PDF-Parsing, Check-In)
├── network/            # API-Client und Service
├── model/              # Domänenobjekte
└── dto/                # Data Transfer Objects
```

## Installation

1. Repository klonen:
   ```bash
   git clone https://github.com/jburis/DA_Prototyp.git
   ```

2. In Android Studio öffnen


3. Projekt synchronisieren und auf Gerät/Emulator ausführen

## Konfiguration

Die Backend-URL ist in `ApiClient.java` definiert:
```java
private static final String BASE_URL = "https://projekte.tgm.ac.at/keci/";
```

## Hauptfunktionen

### PDF-Import
- Extrahiert Veranstaltungsdaten (Name, Datum, Ort)
- Parst Teilnehmerlisten (Name, Bestellnummer, Plätze)
- Verarbeitung erfolgt lokal auf dem Gerät

### Check-In
- Automatische Erkennung von QR-Code oder Bestellnummer
- Kombinierter Analyzer – kein manueller Moduswechsel nötig
- 3-Sekunden-Cooldown gegen Doppelerkennungen

### Datensynchronisation
- Pull-to-Refresh für manuelle Aktualisierung
- Automatisches Laden vor jedem Scanvorgang
- Mehrere Geräte können gleichzeitig arbeiten

## Datenschutz

- PDF-Extraktion und Texterkennung erfolgen **lokal auf dem Gerät**
- Keine Übertragung von Teilnehmerdaten an externe Server (außer Backend)
- Keine Internetverbindung für Scan-Funktionen erforderlich

## Autoren

- **Julian Burisic** – Android Frontend, Projektleitung
- Kollegin – Backend (Express.js/Node.js)

## Lizenz

Dieses Projekt wurde im Rahmen einer Diplomarbeit am TGM Wien entwickelt.