package com.yzb.btplus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;

/**
 * 把「看番 / 刷视频 / 日志」注入 BT 原版左侧菜单栏。
 * 如果电视版侧边栏结构对不上（字段名被改 / 还没建好），
 * 6 秒后自动降级成右上角悬浮按钮，保证一定能进去。
 */
public final class SidebarInjector {

    private static final String TAG_ALL = "btplus_menu_added";
    private static boolean injected = false;

    private SidebarInjector() {
    }

    public static void schedule(final Activity a) {
        if (a == null) return;
        try {
            Ui.init(a);
            Log.init(a);
            Log.installCrashHandler(a.getApplicationContext());
        } catch (Throwable t) {
            // 绝不能因为日志/工具类初始化失败拖垮原版主界面
        }
        injected = false;
        final Handler h = new Handler(Looper.getMainLooper());
        for (int i = 0; i < 10; i++) {
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        inject(a);
                    } catch (Throwable t) {
                        Log.e("Sidebar", "inject failed", t);
                    }
                }
            }, 600L + i * 700L);
        }
        // 原版会在切页/换配置时重建侧边栏(a0)，重建后我们的项会丢，这里持续补回
        try {
            final View decor = a.getWindow().getDecorView();
            decor.getViewTreeObserver().addOnGlobalLayoutListener(
                    new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                        private long last = 0L;

                        @Override
                        public void onGlobalLayout() {
                            long now = System.currentTimeMillis();
                            if (now - last < 800L) return;
                            last = now;
                            try {
                                inject(a);
                            } catch (Throwable ignored) {
                            }
                        }
                    });
        } catch (Throwable t) {
            Log.e("Sidebar", "layout listener failed", t);
        }
        // 12 秒还没注入成功 -> 悬浮按钮兜底
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!injected) {
                    try {
                        floating(a);
                    } catch (Throwable t) {
                        Log.e("Sidebar", "floating failed", t);
                    }
                }
            }
        }, 12000L);
    }

    public static void inject(Activity a) {
        if (a == null) return;
        LinearLayout bar = sidebar(a);
        if (bar == null || bar.getChildCount() == 0) {
            Log.i("Sidebar", "sidebar not ready: " + (bar == null ? "field null" : "children 0"));
            return;
        }
        if (bar.findViewWithTag(TAG_ALL) != null) {
            injected = true;
            return;
        }

        float textSize = 15f;
        int textColor = 0xFFEDEFF5;
        Typeface tf = Typeface.DEFAULT;
        int padL = Ui.dp(18), padT = Ui.dp(12), padR = Ui.dp(18), padB = Ui.dp(12);
        TextView sample = findText(bar.getChildAt(0));
        if (sample != null) {
            textSize = sample.getTextSize() / a.getResources().getDisplayMetrics().scaledDensity;
            textColor = sample.getCurrentTextColor();
            if (sample.getTypeface() != null) tf = sample.getTypeface();
            padL = sample.getPaddingLeft();
            padT = sample.getPaddingTop();
            padR = sample.getPaddingRight();
            padB = sample.getPaddingBottom();
        }

        final Activity act = a;
        addItem(bar, "看番", textSize, textColor, tf, padL, padT, padR, padB,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        open(act, PlusActivity.class);
                    }
                });
        addItem(bar, "刷视频", textSize, textColor, tf, padL, padT, padR, padB,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        open(act, FeedActivity.class);
                    }
                });
        addItem(bar, "日志", textSize, textColor, tf, padL, padT, padR, padB,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        open(act, LogActivity.class);
                    }
                });
        injected = true;
        Log.i("Sidebar", "injected ok, children=" + bar.getChildCount());
    }

    private static void open(Activity a, Class<?> cls) {
        try {
            a.startActivity(new Intent(a, cls));
        } catch (Throwable t) {
            Log.e("Sidebar", "open " + cls.getSimpleName() + " failed", t);
            Ui.toast(a, "打开失败：" + t.getClass().getSimpleName());
        }
    }

    /** 兜底：在 Activity 根布局右上角加一个半透明按钮 */
    private static void floating(final Activity a) {
        View decor = a.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) return;
        FrameLayout root = (FrameLayout) decor;
        if (root.findViewWithTag("btplus_float") != null) return;

        LinearLayout box = Ui.vbox(a);
        box.setTag("btplus_float");

        TextView open = Ui.text(a, "看番 / 刷视频", 13, Color.WHITE);
        open.setGravity(Gravity.CENTER);
        open.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        Ui.focusable(open);
        Ui.addFocusGlow(open,
                Ui.round(Color.parseColor("#CC1A1D26"), 10),
                Ui.round(Ui.PINK, 10));
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    a.startActivity(new Intent(a, PlusActivity.class));
                } catch (Throwable t) {
                    Log.e("Sidebar", "float open", t);
                }
            }
        });
        box.addView(open);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.gravity = Gravity.TOP | Gravity.RIGHT;
        lp.topMargin = Ui.dp(8);
        lp.rightMargin = Ui.dp(8);
        root.addView(box, lp);
        Log.i("Sidebar", "fallback floating button added");
    }

    private static void addItem(LinearLayout bar, String label, float textSize, int textColor,
                                Typeface tf, int padL, int padT, int padR, int padB,
                                View.OnClickListener click) {
        Context c = bar.getContext();

        // 原版每个菜单项都是一个 FrameLayout（NativeMainActivity.k:[FrameLayout]），
        // 这里也包成 FrameLayout，避免原版按索引取子控件时 ClassCastException。
        FrameLayout item = new FrameLayout(c);
        item.setFocusable(true);
        item.setFocusableInTouchMode(false);
        item.setClickable(true);
        item.setTag(TAG_ALL);

        TextView tv = new TextView(c);
        tv.setText(label);
        tv.setTextSize(textSize);
        tv.setTextColor(textColor);
        tv.setTypeface(tf);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(padL, padT, padR, padB);
        item.addView(tv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        GradientDrawable normal = Ui.round(0x00000000, 10);
        GradientDrawable focused = Ui.round(0x33FF3D71, 10);
        Ui.addFocusGlow(item, normal, focused);
        item.setOnClickListener(click);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        try {
            bar.addView(item, lp);
        } catch (Throwable t) {
            Log.e("Sidebar", "addView failed", t);
        }
    }

    private static TextView findText(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            if (t.getText() != null && t.getText().length() > 0) return t;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView t = findText(g.getChildAt(i));
                if (t != null) return t;
            }
        }
        return null;
    }

    private static LinearLayout sidebar(Activity a) {
        try {
            Field f = a.getClass().getField("l");
            Object o = f.get(a);
            if (o instanceof LinearLayout) return (LinearLayout) o;
        } catch (Throwable t) {
            Log.e("Sidebar", "reflect l failed", t);
        }
        return null;
    }
}
