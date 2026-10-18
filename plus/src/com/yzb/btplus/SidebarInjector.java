package com.yzb.btplus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;

public final class SidebarInjector {

    private static final String TAG_ALL = "btplus_menu_added";

    private SidebarInjector() {
    }

    public static void schedule(final Activity a) {
        if (a == null) {
            return;
        }
        Ui.init(a);
        final Handler h = new Handler(Looper.getMainLooper());
        for (int i = 0; i < 14; i++) {
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        inject(a);
                    } catch (Throwable ignored) {
                    }
                }
            }, 600L + i * 800L);
        }
    }

    public static void inject(Activity a) {
        if (a == null) {
            return;
        }
        LinearLayout bar = sidebar(a);
        if (bar == null || bar.getChildCount() == 0) {
            return;
        }
        if (TAG_ALL.equals(bar.getTag())) {
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
            if (sample.getTypeface() != null) {
                tf = sample.getTypeface();
            }
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
                        try {
                            act.startActivity(new Intent(act, PlusActivity.class));
                        } catch (Throwable t) {
                            Ui.toast(act, "打开失败");
                        }
                    }
                });
        addItem(bar, "刷视频", textSize, textColor, tf, padL, padT, padR, padB,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            act.startActivity(new Intent(act, FeedActivity.class));
                        } catch (Throwable t) {
                            Ui.toast(act, "打开失败");
                        }
                    }
                });
        bar.setTag(TAG_ALL);
    }

    private static void addItem(LinearLayout bar, String label, float textSize, int textColor,
                                Typeface tf, int padL, int padT, int padR, int padB,
                                View.OnClickListener click) {
        Context c = bar.getContext();
        TextView tv = new TextView(c);
        tv.setText(label);
        tv.setTextSize(textSize);
        tv.setTextColor(textColor);
        tv.setTypeface(tf);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(padL, padT, padR, padB);
        tv.setFocusable(true);
        tv.setFocusableInTouchMode(false);
        tv.setClickable(true);

        GradientDrawable normal = Ui.round(0x00000000, 10);
        GradientDrawable focused = Ui.round(0x33FF3D71, 10);
        Ui.addFocusGlow(tv, normal, focused);
        tv.setOnClickListener(click);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bar.addView(tv, lp);
    }

    private static TextView findText(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            if (t.getText() != null && t.getText().length() > 0) {
                return t;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView t = findText(g.getChildAt(i));
                if (t != null) {
                    return t;
                }
            }
        }
        return null;
    }

    private static LinearLayout sidebar(Activity a) {
        try {
            Field f = a.getClass().getField("l");
            Object o = f.get(a);
            if (o instanceof LinearLayout) {
                return (LinearLayout) o;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
