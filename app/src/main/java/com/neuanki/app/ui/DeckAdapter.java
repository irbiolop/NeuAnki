package com.neuanki.app.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.neuanki.app.R;
import com.neuanki.app.db.Deck;
import com.neuanki.app.util.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** آداپتور لیست دک‌ها */
public class DeckAdapter extends RecyclerView.Adapter<DeckAdapter.VH> {

    public interface Listener {
        void onOpen(Deck d);
        void onMenu(Deck d, View anchor);
    }

    private final List<Deck> items = new ArrayList<>();
    private final Map<Long, int[]> counts = new HashMap<>();
    private final Listener listener;

    public DeckAdapter(Listener l) {
        listener = l;
    }

    public void setData(List<Deck> decks, Map<Long, int[]> cnt) {
        items.clear();
        if (decks != null) items.addAll(decks);
        counts.clear();
        if (cnt != null) counts.putAll(cnt);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_deck, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        Deck d = items.get(pos);
        h.name.setText(d.name);
        int[] c = counts.get(d.id);
        int nw = c == null ? 0 : c[0];
        int lr = c == null ? 0 : c[1];
        int rv = c == null ? 0 : c[2];
        h.tvNew.setText(Util.fa(nw));
        h.tvLearn.setText(Util.fa(lr));
        h.tvDue.setText(Util.fa(rv));
        h.root.setOnClickListener(v -> {
            if (listener != null) listener.onOpen(d);
        });
        h.root.setOnLongClickListener(v -> {
            if (listener != null) listener.onMenu(d, v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        View root;
        TextView name, tvNew, tvLearn, tvDue;

        VH(View v) {
            super(v);
            root = v;
            name = v.findViewById(R.id.tvDeckName);
            tvNew = v.findViewById(R.id.tvNew);
            tvLearn = v.findViewById(R.id.tvLearn);
            tvDue = v.findViewById(R.id.tvDue);
        }
    }
}
