package com.example.da_prototyp_ocr.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.da_prototyp_ocr.R;
import com.example.da_prototyp_ocr.model.Buchung;

import java.util.List;

public class ParticipantAdapter extends BaseAdapter {

    private final Context context;
    private final List<Buchung> bookings;

    public ParticipantAdapter(Context context, List<Buchung> bookings) {
        this.context = context;
        this.bookings = bookings;
    }

    @Override
    public int getCount() {
        return bookings.size();
    }

    @Override
    public Object getItem(int position) {
        return bookings.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_participant, parent, false);
            holder = new ViewHolder();
            holder.nameText = convertView.findViewById(R.id.nameText);
            holder.orderNumberText = convertView.findViewById(R.id.orderNumberText);
            holder.statusText = convertView.findViewById(R.id.statusText);
            holder.statusIndicator = convertView.findViewById(R.id.statusIndicator);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Buchung booking = bookings.get(position);

    
        String displayName = booking.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = booking.getVorname() + " " + booking.getNachname();
        }
        holder.nameText.setText(displayName);

        // Order number
        holder.orderNumberText.setText(booking.getBestellnummer() != null ? booking.getBestellnummer() : "");

       
        int checkedIn = booking.getCheckedInCount();
        int total = booking.getAnzahlPlaetze();
        holder.statusText.setText(checkedIn + "/" + total + " checked in");

        int color;
        if (checkedIn == 0) {
            // Gray - nobody checked in
            color = Color.parseColor("#9E9E9E");
        } else if (checkedIn < total) {
            // Orange - partially checked in
            color = Color.parseColor("#FF9800");
        } else {
            // Green - fully checked in
            color = Color.parseColor("#4CAF50");
        }
        holder.statusIndicator.setBackgroundColor(color);
        holder.statusText.setTextColor(color);

        return convertView;
    }

    static class ViewHolder {
        TextView nameText;
        TextView orderNumberText;
        TextView statusText;
        View statusIndicator;
    }
}
