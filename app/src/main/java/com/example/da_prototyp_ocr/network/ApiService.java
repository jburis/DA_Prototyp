package com.example.da_prototyp_ocr.network;
import com.example.da_prototyp_ocr.model.Anwesenheit;
import com.example.da_prototyp_ocr.model.Attendee;
import com.example.da_prototyp_ocr.dto.AttendeeIdResponse;
import com.example.da_prototyp_ocr.model.Buchung;
import com.example.da_prototyp_ocr.dto.CheckInRequest;
import com.example.da_prototyp_ocr.dto.CheckinByBestellnummerRequest;
import com.example.da_prototyp_ocr.dto.CheckinByNameRequest;
import com.example.da_prototyp_ocr.dto.CheckinByNameResponse;
import com.example.da_prototyp_ocr.dto.ImportBuchungenRequest;
import com.example.da_prototyp_ocr.dto.ImportBuchungenResponse;
import com.example.da_prototyp_ocr.model.Veranstaltung;
import com.example.da_prototyp_ocr.dto.VeranstaltungCreateRequest;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Header;
import retrofit2.http.DELETE;

public interface ApiService {

    /**
     * Erstellt einen neuen Teilnehmer.
     * @param attendee Das Teilnehmer-Objekt, das als JSON-Body gesendet wird.
     */
    @POST("api/attendees")
    Call<AttendeeIdResponse> createAttendee(@Body Attendee attendee);

    /**
     * Ruft eine Liste von Teilnehmern ab, optional gefiltert nach einem Suchbegriff.
     * @param query Der Suchbegriff (kann null oder leer sein).
     */
    @GET("api/attendees")
    Call<List<Attendee>> getAttendees(@Query("query") String query);

    /**
     * Checkt eine bestimmte Anzahl von Gästen für eine Bestellnummer ein.
     * @param orderNumber Die Bestellnummer aus der URL.
     * @param checkInRequest Der Request-Body, der die Anzahl enthält.
     */
    @POST("api/checkin/{order}")
    Call<Attendee> checkIn(@Path("order") String orderNumber, @Body CheckInRequest checkInRequest);

    /**
     * Checkt eine bestimmte Anzahl von Gästen für eine Bestellnummer aus.
     * @param orderNumber Die Bestellnummer aus der URL.
     * @param checkInRequest Der Request-Body, der die Anzahl enthält.
     */
    @POST("api/uncheck/{order}")
    Call<Attendee> unCheck(@Path("order") String orderNumber, @Body CheckInRequest checkInRequest);

    /**
     * Checkt einen Teilnehmer anhand seines Namens ein.
     * @param attendee Das Teilnehmer-Objekt, das mindestens den Namen enthalten muss.
     */
    @POST("api/checkin-by-name")
    Call<Attendee> checkInLegacyByName(@Body Attendee attendee);

    //AB HIER WERDEN NEUE HINZUGEFÜGT

    @GET("api/veranstaltungen")
    Call<List<Veranstaltung>> getVeranstaltungen();


    // Admin: Veranstaltung anlegen
    @POST("api/veranstaltungen")
    Call<Veranstaltung> createVeranstaltung(
            @Header("x-admin-token") String adminToken,
            @Body VeranstaltungCreateRequest body
    );

    // Admin: Buchungen importieren
    @POST("api/veranstaltungen/{veranstaltung_id}/buchungen/import")
    Call<ImportBuchungenResponse> importBuchungen(
            @Header("x-admin-token") String adminToken,
            @Path("veranstaltung_id") int veranstaltungId,
            @Body ImportBuchungenRequest body
    );


    // 1) Buchungen (Teilnehmerliste) einer Veranstaltung laden
    @GET("api/buchungen/veranstaltung/{veranstaltung_id}")
    Call<List<Buchung>> getBuchungenByVeranstaltung(@Path("veranstaltung_id") int veranstaltungId);

    // 2) Anwesenheiten einer Veranstaltung laden (für checked-in Summen)
    @GET("api/anwesenheiten/veranstaltung/{veranstaltung_id}")
    Call<List<Anwesenheit>> getAnwesenheitenByVeranstaltung(@Path("veranstaltung_id") int veranstaltungId);

    // 3) Check-in per Bestellnummer (Admin)
    @POST("api/anwesenheiten/veranstaltung/{veranstaltung_id}/checkin/bestellnummer")
    Call<Anwesenheit> checkInByBestellnummer(
            @Header("x-admin-token") String adminToken,
            @Path("veranstaltung_id") int veranstaltungId,
            @Body CheckinByBestellnummerRequest body
    );

    // 4) Check-in per Name (Admin) -> Response ist { hinweis, result }
    @POST("api/anwesenheiten/veranstaltung/{veranstaltung_id}/checkin/name")
    Call<CheckinByNameResponse> checkInByName(
            @Header("x-admin-token") String adminToken,
            @Path("veranstaltung_id") int veranstaltungId,
            @Body CheckinByNameRequest body
    );

    // 5) Neue Buchung erstellen (requires admin token)
    @POST("api/buchungen")
    Call<Buchung> createBuchung(
            @Header("x-admin-token") String adminToken,
            @Body Buchung buchung
    );

    // 6) Buchung löschen
    @DELETE("api/buchungen/{buchung_id}")
    Call<Void> deleteBuchung(@Path("buchung_id") int buchungId);
}