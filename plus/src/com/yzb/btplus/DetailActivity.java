package com.yzb.btplus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 详情页：封面 + 简介 + 选集（遥控器左右上下移动，OK 播放） */
public class DetailActivity extends Activity {

    private Api.Item item;
    private LinearLayout epsBox;
    private TextView tvTitle;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Log.init(this);
        try {
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));
            item = (Api.Item) getIntent().getSerializableExtra("item");
            if (item == null) {
                finish();
                return;
            }
            build();
        } catch (Throwable t) {
            Ui.showFatal(this, t);
        }
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout page = Ui.vbox(this);
        page.setPadding(Ui.dp(32), Ui.dp(22), Ui.dp(32), 0);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // 顶栏
        LinearLayout top = Ui.hbox(this);
        TextView back = Ui.text(this, "← 返回", 14, Ui.TXT);
        back.setGravity(Gravity.CENTER);
        back.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        Ui.focusable(back);
        Ui.addFocusGlow(back, Ui.round(Color.parseColor("#1A1D26"), 9), Ui.round(Ui.PINK, 9));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(back);

        tvTitle = Ui.text(this, "  " + item.name, 22, Ui.TXT);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        tvTitle.setMaxLines(1);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(tvTitle, Ui.lpL(0, -2, 1f));
        page.addView(top, Ui.lpL(-1, -2));

        // 主体
        LinearLayout body = Ui.hbox(this);
        body.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams blp = Ui.lpL(-1, 0, 1f);
        blp.topMargin = Ui.dp(16);
        page.addView(body, blp);

        // 封面
        FrameLayout coverBox = new FrameLayout(this);
        coverBox.setBackgroundDrawable(Ui.round(Color.parseColor("#0A0C10"), 12));
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverBox.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        Ui.loadImage(iv, item.pic);
        LinearLayout.LayoutParams clp = Ui.lpL(Ui.dp(230), Ui.dp(320));
        clp.rightMargin = Ui.dp(28);
        body.addView(coverBox, clp);

        // 右侧信息
        LinearLayout right = Ui.vbox(this);
        body.addView(right, Ui.lpL(0, -1, 1f));

        String meta = join(" / ", item.typeName, item.year, item.area);
        if (meta.length() > 0) {
            TextView mt = Ui.text(this, meta, 13, Ui.PINK);
            right.addView(mt);
        }
        if (item.remark != null && item.remark.length() > 0) {
            TextView rm = Ui.text(this, item.remark, 13, Ui.TXT2);
            LinearLayout.LayoutParams rlp = Ui.lpL(-1, -2);
            rlp.topMargin = Ui.dp(6);
            right.addView(rm, rlp);
        }

        TextView lab = Ui.text(this, "选集（" + item.eps.size() + "）", 15, Ui.TXT);
        lab.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams llp = Ui.lpL(-1, -2);
        llp.topMargin = Ui.dp(20);
        llp.bottomMargin = Ui.dp(10);
        right.addView(lab, llp);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        epsBox = Ui.vbox(this);
        sv.addView(epsBox, new FrameLayout.LayoutParams(-1, -2));
        right.addView(sv, Ui.lpL(-1, 0, 1f));

        renderEps();

        setContentView(root);

        // 默认聚焦第一个可播放的
        root.post(new Runnable() {
            @Override
            public void run() {
                if (epsBox.getChildCount() > 0) epsBox.getChildAt(0).requestFocus();
            }
        });
    }

    private static String join(String sep, String... arr) {
        StringBuilder sb = new StringBuilder();
        for (String s : arr) {
            if (s == null || s.trim().length() == 0) continue;
            if (sb.length() > 0) sb.append(sep);
            sb.append(s.trim());
        }
        return sb.toString();
    }

    private static final int PER_ROW = 6;

    private void renderEps() {
        epsBox.removeAllViews();
        List<String[]> eps = item.eps;
        if (eps.isEmpty()) {
            epsBox.addView(Ui.text(this, "没有可播放的剧集", 14, Ui.TXT2));
            return;
        }
        LinearLayout row = null;
        for (int i = 0; i < eps.size(); i++) {
            if (i % PER_ROW == 0) {
                row = Ui.hbox(this);
                LinearLayout.LayoutParams rlp = Ui.lpL(-1, -2);
                rlp.bottomMargin = Ui.dp(10);
                epsBox.addView(row, rlp);
            }
            final String[] ep = eps.get(i);
            TextView t = Ui.text(this, ep[0], 13, Ui.TXT);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            Ui.focusable(t);
            Ui.addFocusGlow(t,
                    Ui.roundStroke(Color.parseColor("#1A1D26"), 9, Color.parseColor("#2A2F3B"), 1),
                    Ui.round(Ui.PINK, 9));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    play(ep);
                }
            });
            LinearLayout.LayoutParams tlp = Ui.lpL(0, Ui.dp(40), 1f);
            tlp.rightMargin = Ui.dp(9);
            row.addView(t, tlp);
        }
        if (row != null) {
            int filled = eps.size() % PER_ROW;
            if (filled != 0) {
                for (int i = filled; i < PER_ROW; i++) row.addView(new View(this), Ui.lpL(0, 1, 1f));
            }
        }
    }

    private void play(String[] ep) {
        Intent i = new Intent(this, PlayerActivity.class);
        i.putExtra("url", ep[1]);
        i.putExtra("title", item.name + " · " + ep[0]);
        i.putStringArrayListExtra("names", names());
        i.putStringArrayListExtra("urls", urls());
        i.putExtra("index", indexOf(ep[1]));
        startActivity(i);
    }

    private ArrayList<String> names() {
        ArrayList<String> a = new ArrayList<String>();
        for (String[] e : item.eps) a.add(e[0]);
        return a;
    }

    private ArrayList<String> urls() {
        ArrayList<String> a = new ArrayList<String>();
        for (String[] e : item.eps) a.add(e[1]);
        return a;
    }

    private int indexOf(String url) {
        for (int i = 0; i < item.eps.size(); i++) {
            if (item.eps.get(i)[1].equals(url)) return i;
        }
        return 0;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(code, e);
    }
}
