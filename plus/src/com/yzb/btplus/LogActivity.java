package com.yzb.btplus;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 日志查看页：电视上没有 logcat，直接把日志显示在屏幕上 */
public class LogActivity extends Activity {

    private TextView body;
    private ScrollView sv;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Log.init(this);
        Log.i("LogActivity", "open log viewer");
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout page = Ui.vbox(this);
        page.setPadding(Ui.dp(28), Ui.dp(18), Ui.dp(28), Ui.dp(10));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = Ui.hbox(this);
        TextView title = Ui.text(this, "运行日志", 22, Ui.TXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(title);

        TextView hint = Ui.text(this, "  " + Log.dir(), 11, Ui.TXT2);
        top.addView(hint);

        View sp = new View(this);
        top.addView(sp, Ui.lpL(0, 1, 1f));

        TextView refresh = Ui.text(this, "刷新", 14, Ui.TXT);
        refresh.setGravity(Gravity.CENTER);
        refresh.setPadding(Ui.dp(18), Ui.dp(8), Ui.dp(18), Ui.dp(8));
        Ui.focusable(refresh);
        Ui.addFocusGlow(refresh, Ui.roundStroke(Color.parseColor("#1A1D26"), 9, Color.parseColor("#2E3340"), 1), Ui.round(Ui.PINK, 9));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                render();
            }
        });
        top.addView(refresh);

        TextView back = Ui.text(this, "返回", 14, Ui.TXT);
        back.setGravity(Gravity.CENTER);
        back.setPadding(Ui.dp(18), Ui.dp(8), Ui.dp(18), Ui.dp(8));
        Ui.focusable(back);
        Ui.addFocusGlow(back, Ui.roundStroke(Color.parseColor("#1A1D26"), 9, Color.parseColor("#2E3340"), 1), Ui.round(Ui.PINK, 9));
        LinearLayout.LayoutParams blp = Ui.lpL(-2, -2);
        blp.leftMargin = Ui.dp(10);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(back, blp);

        page.addView(top, Ui.lpL(-1, -2));

        TextView dev = Ui.text(this, Log.deviceInfo(), 12, Ui.TXT2);
        LinearLayout.LayoutParams dlp = Ui.lpL(-1, -2);
        dlp.topMargin = Ui.dp(6);
        dlp.bottomMargin = Ui.dp(8);
        page.addView(dev, dlp);

        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(true);
        body = Ui.text(this, "", 11, Color.parseColor("#C9CEDB"));
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        sv.addView(body, new FrameLayout.LayoutParams(-1, -2));
        page.addView(sv, Ui.lpL(-1, 0, 1f));

        setContentView(root);
        render();
        refresh.requestFocus();
    }

    private void render() {
        try {
            body.setText(Log.dump());
            sv.post(new Runnable() {
                @Override
                public void run() {
                    sv.fullScroll(View.FOCUS_DOWN);
                }
            });
        } catch (Throwable t) {
            body.setText("读取日志失败: " + t);
        }
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            render();
            return true;
        }
        return super.onKeyDown(code, e);
    }
}
