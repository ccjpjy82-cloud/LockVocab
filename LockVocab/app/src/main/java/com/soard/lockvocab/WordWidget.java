package com.soard.lockvocab;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class WordWidget extends AppWidgetProvider {
    static final String PREV = "com.soard.lockvocab.PREV";
    static final String NEXT = "com.soard.lockvocab.NEXT";
    static final String TODAY = "com.soard.lockvocab.TODAY";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) draw(c, m, id);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        super.onReceive(c, i);
        String a = i.getAction();
        if (PREV.equals(a) || NEXT.equals(a) || TODAY.equals(a)) {
            SharedPreferences p = c.getSharedPreferences("w", 0);
            LocalDate d = sel(p);
            if (PREV.equals(a)) d = d.minusDays(1);
            else if (NEXT.equals(a)) d = d.plusDays(1);
            else d = LocalDate.now();
            p.edit().putString("sel", d.toString()).apply();
            refresh(c);
        }
    }

    static LocalDate sel(SharedPreferences p) {
        try {
            String s = p.getString("sel", "");
            if (!s.isEmpty()) return LocalDate.parse(s);
        } catch (Exception e) { }
        return LocalDate.now();
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, WordWidget.class))) draw(c, m, id);
    }

    static PendingIntent pi(Context c, String action, int code) {
        Intent n = new Intent(c, WordWidget.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, n,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void draw(Context c, AppWidgetManager m, int id) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        SharedPreferences p = c.getSharedPreferences("w", 0);
        LocalDate d = sel(p);
        String text = "";
        try {
            text = new JSONObject(p.getString("words", "{}")).optString(d.toString(), "");
        } catch (Exception e) { }
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (String line : text.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] q = line.split("\\|");
            String w = q[0].trim();
            String mm = q.length > 1 ? q[1].trim() : "";
            String e = q.length > 2 ? q[2].trim() : "";
            int s = sb.length();
            sb.append(w);
            sb.setSpan(new StyleSpan(Typeface.BOLD), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new ForegroundColorSpan(0xFFFFFFFF), s, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (!mm.isEmpty()) {
                int s2 = sb.length();
                sb.append("  ").append(mm);
                sb.setSpan(new ForegroundColorSpan(0xFFA9D9C9), s2, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (!e.isEmpty()) {
                int s3 = sb.length();
                sb.append("\n").append(e);
                sb.setSpan(new ForegroundColorSpan(0xFF8A9A94), s3, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new RelativeSizeSpan(0.85f), s3, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            sb.append("\n\n");
        }
        if (sb.length() == 0) sb.append("이 날 적은 단어가 없어요");
        v.setTextViewText(R.id.d, d.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)));
        v.setTextViewText(R.id.body, sb);
        v.setOnClickPendingIntent(R.id.prev, pi(c, PREV, 1));
        v.setOnClickPendingIntent(R.id.next, pi(c, NEXT, 2));
        v.setOnClickPendingIntent(R.id.d, pi(c, TODAY, 3));
        m.updateAppWidget(id, v);
    }
}
