package com.neuanki.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.neuanki.app.R;
import com.neuanki.app.db.Store;
import com.neuanki.app.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** آمار و نمودارها */
public class StatsActivity extends Base {

    private static final int TEXT_MUTED = 0xFF98A2AC;
    private static final int GRID = 0x26FFFFFF;

    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView tvTotal, tvToday, tvStreak, tvCorrect;
    private BarChart chartReviews, chartForecast;
    private PieChart chartPie;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        tvTotal = findViewById(R.id.tvTotalVal);
        tvToday = findViewById(R.id.tvTodayVal);
        tvStreak = findViewById(R.id.tvStreakVal);
        tvCorrect = findViewById(R.id.tvCorrectVal);
        chartReviews = findViewById(R.id.chartReviews);
        chartForecast = findViewById(R.id.chartForecast);
        chartPie = findViewById(R.id.chartPie);

        load();
    }

    private void load() {
        exec.execute(() -> {
            long now = System.currentTimeMillis();
            long today = Util.dayNum(now);

            final int total = Store.totalCards(this);
            final int todayRev = Store.reviewsOnDay(this, today);
            final int streak = Store.streak(this, today);
            final int correct = Store.correctPct(this);
            final Map<Long, Integer> reviews = Store.reviewsPerDay(this, 30, today);
            final Map<Long, Integer> forecast = Store.forecast(this, today, 30);
            final int[] dist = Store.stateCounts(this);

            ui.post(() -> {
                tvTotal.setText(Util.fa(total));
                tvToday.setText(Util.fa(todayRev));
                tvStreak.setText(Util.fa(streak));
                tvCorrect.setText(correct < 0 ? "—" : Util.fa(correct) + "٪");

                setupBar(chartReviews, reviews, 0xFF34D399, false);
                setupBar(chartForecast, forecast, 0xFF60A5FA, true);
                setupPie(dist);
            });
        });
    }

    private String dayLabel(long day, long today, boolean forecastMode) {
        long diff = day - today;
        if (forecastMode) {
            if (diff == 0) return getString(R.string.today);
            return "+" + Util.fa(diff);
        }
        long back = today - day;
        if (back == 0) return getString(R.string.today);
        return "-" + Util.fa(back);
    }

    private void setupBar(BarChart chart, Map<Long, Integer> data, int color, boolean forecastMode) {
        long today = Util.dayNum(System.currentTimeMillis());
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int i = 0;
        for (Map.Entry<Long, Integer> e : data.entrySet()) {
            entries.add(new BarEntry(i, e.getValue()));
            labels.add(dayLabel(e.getKey(), today, forecastMode));
            i++;
        }

        chart.setBackgroundColor(Color.TRANSPARENT);
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataText(getString(R.string.no_results));
        chart.setNoDataTextColor(TEXT_MUTED);
        chart.setDrawGridBackground(false);
        chart.setScaleYEnabled(false);

        XAxis x = chart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setTextColor(TEXT_MUTED);
        x.setGridColor(Color.TRANSPARENT);
        x.setAxisLineColor(GRID);
        x.setGranularity(1f);
        x.setLabelCount(5, false);
        x.setValueFormatter(new IndexAxisValueFormatter(labels));

        YAxis y = chart.getAxisLeft();
        y.setTextColor(TEXT_MUTED);
        y.setGridColor(GRID);
        y.setAxisLineColor(Color.TRANSPARENT);
        y.setAxisMinimum(0f);
        y.setGranularity(1f);
        y.setSpaceTop(12f);

        chart.getAxisRight().setEnabled(false);

        BarDataSet ds = new BarDataSet(entries, "");
        ds.setColor(color);
        ds.setHighLightColor(Color.TRANSPARENT);
        ds.setDrawValues(false);
        BarData bd = new BarData(ds);
        bd.setBarWidth(0.82f);
        chart.setData(bd);
        chart.animateY(450);
        chart.invalidate();
    }

    private void setupPie(int[] dist) {
        String[] names = {
                getString(R.string.dist_new),
                getString(R.string.dist_learn),
                getString(R.string.dist_young),
                getString(R.string.dist_mature),
                getString(R.string.dist_susp)
        };
        int[] colors = {0xFF4FC3F7, 0xFFEF5350, 0xFFFBBF24, 0xFF34D399, 0xFF98A2AC};

        List<PieEntry> entries = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        for (int i = 0; i < dist.length; i++) {
            if (dist[i] <= 0) continue;
            entries.add(new PieEntry(dist[i], names[i]));
            cols.add(colors[i]);
        }

        chartPie.setBackgroundColor(Color.TRANSPARENT);
        chartPie.getDescription().setEnabled(false);
        chartPie.setHoleColor(Color.TRANSPARENT);
        chartPie.setDrawHoleEnabled(true);
        chartPie.setHoleRadius(55f);
        chartPie.setTransparentCircleAlpha(0);
        chartPie.setEntryLabelColor(Color.TRANSPARENT);
        chartPie.setNoDataText(getString(R.string.no_results));
        chartPie.setNoDataTextColor(TEXT_MUTED);
        chartPie.getLegend().setTextColor(TEXT_MUTED);
        chartPie.getLegend().setTextSize(12f);
        chartPie.getLegend().setFormSize(11f);

        if (!entries.isEmpty()) {
            PieDataSet ds = new PieDataSet(entries, "");
            ds.setColors(cols);
            ds.setSliceSpace(2.5f);
            ds.setSelectionShift(4f);
            PieData pd = new PieData(ds);
            pd.setValueTextSize(11f);
            pd.setValueTextColor(0xFFE7EAED);
            pd.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return Util.fa(Math.round(value));
                }
            });
            chartPie.setData(pd);
        } else {
            chartPie.setData(null);
        }
        chartPie.animateY(450);
        chartPie.invalidate();
    }
}
