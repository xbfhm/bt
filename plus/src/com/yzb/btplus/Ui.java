package com.yzb.btplus;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 纯 framework 的 UI 工具 + 图片加载（不依赖任何第三方库） */
public class Ui {

    public static final int BG = Color.parseColor("#0E1015");
    public static final int CARD = Color.parseColor("#1A1D26");
    public static final int PINK = Color.parseColor("#FF3D71");
    public static final int TXT = Color.parseColor("#EDEFF5");
    public static final int TXT2 = Color.parseColor("#8B90A0");

    private static float density = 3f;

    public static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
    }

    public static int dp(float v) {
        return (int) (v * density + 0.5f);
    }

    public static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    public static GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    public static GradientDrawable roundStroke(int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable g = round(color, radiusDp);
        g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    /** 可聚焦的卡片（遥控器友好） */
    public static void focusable(View v) {
        v.setFocusable(true);
        v.setFocusableInTouchMode(false);
    }

    public static void addFocusGlow(final View v, final GradientDrawable normal, final GradientDrawable focused) {
        v.setBackgroundDrawable(normal);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean has) {
                view.setBackgroundDrawable(has ? focused : normal);
            }
        });
    }

    // ---------------- 图片异步加载 ----------------
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(8 * 1024 * 1024) {
                @Override
                protected int sizeOf(String k, Bitmap b) {
                    return b.getByteCount();
                }
            };

    public static void loadImage(final ImageView iv, final String url) {
        if (url == null || url.length() < 8) return;
        Bitmap c = CACHE.get(url);
        if (c != null) {
            iv.setImageBitmap(c);
            return;
        }
        iv.setTag(url);
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bm = null;
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 5.1.1)");
                    conn.setRequestProperty("Referer", "https://www.ffzyapi.com/");
                    InputStream in = conn.getInputStream();
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inSampleSize = 2;
                    bm = BitmapFactory.decodeStream(in, null, o);
                    in.close();
                } catch (Throwable ignored) {
                } finally {
                    if (conn != null) conn.disconnect();
                }
                final Bitmap fb = bm;
                if (fb == null) return;
                CACHE.put(url, fb);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (url.equals(iv.getTag())) iv.setImageBitmap(fb);
                    }
                });
            }
        });
    }

    public static void runOnUi(Runnable r) {
        MAIN.post(r);
    }

    public static void inBg(Runnable r) {
        POOL.execute(r);
    }

    /** 显示一层遮罩提示 */
    public static void toast(Context c, String msg) {
        android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static FrameLayout.LayoutParams lp(int w, int h) {
        return new FrameLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpL(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpL(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    public static ViewGroup.MarginLayoutParams margin(ViewGroup.MarginLayoutParams p, int l, int t, int r, int b) {
        p.leftMargin = l;
        p.topMargin = t;
        p.rightMargin = r;
        p.bottomMargin = b;
        return p;
    }
}
