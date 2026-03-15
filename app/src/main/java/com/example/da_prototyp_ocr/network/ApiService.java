package com.example.da_prototyp_ocr.network;

import com.example.da_prototyp_ocr.model.Anwesenheit;
import com.example.da_prototyp_ocr.model.Buchung;
import com.example.da_prototyp_ocr.model.Veranstaltung;
import com.example.da_prototyp_ocr.dto.CheckinByBestellnummerRequest;
import com.example.da_prototyp_ocr.dto.CheckinByNameRequest;
import com.example.da_prototyp_ocr.dto.CheckinByNameResponse;
import com.example.da_prototyp_ocr.dto.ImportBuchungenRequest;
import com.example.da_prototyp_ocr.dto.ImportBuchungenResponse;
import com.example.da_prototyp_ocr.dto.VeranstaltungCreateRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {

    // ==================== VERANSTALTUNGEN ====================

    /**
     * Ruft alle Veranstaltungen ab.
     */
    @GET("api/veranstaltungen")
    Call<List<Veranstaltung>> getVeranstaltungen();

    /**
     * Erstellt eine neue Veranstaltung.
     */
    @POST("api/veranstaltungen")
    Call<Veranstaltung> createVeranstaltung(
            @Header("x-admin-token") String adminToken,
            @Body VeranstaltungCreateRequest body
    );

    // ==================== BUCHUNGEN ====================

    /**
     * Ruft alle Buchungen einer Veranstaltung ab.
     */
    @GET("api/buchungen/veranstaltung/{veranstaltung_id}")
    Call<List<Buchung>> getBuchungenByVeranstaltung(
            @Path("veranstaltung_id") int veranstaltungId
    );

    /**
     * Erstellt eine neue Buchung.
     */
    @POST("api/buchungen")
    Call<Buchung> createBuchung(
            @Header("x-admin-token") String adminToken,
            @Body Buchung buchung
    );

    /**
     * Löscht eine Buchung.
     */
    @DELETE("api/buchungen/{buchung_id}")
    Call<Void> deleteBuchung(
            @Path("buchung_id") int buchungId
    );

    /**
     * Importiert mehrere Buchungen auf einmal.
     */
    @POST("api/veranstaltungen/{veranstaltung_id}/buchungen/import")
    Call<ImportBuchungenResponse> importBuchungen(
            @Header("x-admin-token") String adminToken,
            @Path("veranstaltung_id") int veranstaltungId,
            @Body ImportBuchungenRequest body
    );

    // ==================== ANWESENHEITEN / CHECK-IN ====================

    /**
     * Ruft alle Anwesenheiten einer Veranstaltung ab.
     */
    @GET("api/anwesenheiten/veranstaltung/{veranstaltung_id}")
    Call<List<Anwesenheit>> getAnwesenheitenByVeranstaltung(
            @Path("veranstaltung_id") int veranstaltungId
    );

    /**
     * Check-In per Bestellnummer.
     */
    @POST("api/anwesenheiten/veranstaltung/{veranstaltung_id}/checkin/bestellnummer")
    Call<Anwesenheit> checkInByBestellnummer(
            @Header("x-admin-token") String adminToken,
            @Path("veranstaltung_id") int veranstaltungId,
            @Body CheckinByBestellnummerRequest body
    );

    /**
     * Check-In per Name.
     */
    @POST("api/anwesenheiten/veranstaltung/{veranstaltung_id}/checkin/name")
    Call<CheckinByNameResponse> checkInByName(
            @Header("x-admin-token") String adminToken,
            @Path("veranstaltung_id") int veranstaltungId,
            @Body CheckinByNameRequest body
    );
}