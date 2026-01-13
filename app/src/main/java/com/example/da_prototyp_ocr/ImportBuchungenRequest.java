package com.example.da_prototyp_ocr;

import java.util.List;

public class ImportBuchungenRequest {
    public List<ImportBuchungItem> buchungen;

    public ImportBuchungenRequest(List<ImportBuchungItem> buchungen) {
        this.buchungen = buchungen;
    }
}
