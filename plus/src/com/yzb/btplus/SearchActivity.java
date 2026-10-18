package com.yzb.btplus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** 搜索页：输入关键词 + 热词快捷入口 */
public class SearchActivity extends Activity {

    private static final String[] HOT = {
            "庆余年", "斗罗大陆", "凡人修仙传", "三体", "狂飙",
            "鬼灭之刃", "进击的巨人", "间谍过家家", "咒术回战", "葬送的芙莉莲",
            "复仇者联盟", "沙丘", "流浪地球", "奥本海默", "周处除三害"
    };

    private EditText input;
    private LinearLayout gridBox;
    private ProgressBar loading;
    private TextView tvEmpty;
    private ScrollView sv;
    private int curPage = 1;
    private int pageCount = 1;
    private String curKw = "";
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));
        build();
    }

    private void build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout page = Ui.vbox(this);
        page.setPadding(Ui.dp(28), Ui.dp(18), Ui.dp(28), 0);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = Ui.hbox(this);

        TextView back = Ui.text(this, "←", 20, Ui.TXT);
        back.setGravity(Gravity.CENTER);
        Ui.focusable(back);
        Ui.addFocusGlow(back, Ui.round(Color.parseColor("#1A1D26"), 9), Ui.round(Ui.PINK, 9));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(back, Ui.lpL(Ui.dp(44), Ui.dp(42)));

        input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setTextColor(Ui.TXT);
        input.setHintTextColor(Ui.TXT2);
        input.setHint("输入番剧 / 电影名称");
        input.setBackgroundDrawable(Ui.roundStroke(Color.parseColor("#1A1D26"), 10,
                Color.parseColor("#2E3340"), 1));
        input.setPadding(Ui.dp(16), 0, Ui.dp(16), 0);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH
                        || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    doSearch(input.getText().toString());
                    hideIme();
                    return true;
                }
                return false;
            }
        });
        LinearLayout.LayoutParams ilp = Ui.lpL(0, Ui.dp(42), 1f);
        ilp.leftMargin = Ui.dp(12);
        ilp.rightMargin = Ui.dp(12);
        top.addView(input, ilp);

        TextView go = Ui.text(this, "搜索", 14, Color.WHITE);
        go.setGravity(Gravity.CENTER);
        Ui.focusable(go);
        Ui.addFocusGlow(go, Ui.round(Ui.PINK, 10), Ui.round(Color.parseColor("#FF6B90"), 10));
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doSearch(input.getText().toString());
                hideIme();
            }
        });
        top.addView(go, Ui.lpL(Ui.dp(88), Ui.dp(42)));

        page.addView(top, Ui.lpL(-1, -2));

        // 热词
        LinearLayout hotRow = Ui.hbox(this);
        LinearLayout.LayoutParams hrlp = Ui.lpL(-1, -2);
        hrlp.topMargin = Ui.dp(14);
        page.addView(hotRow, hrlp);
        hotRow.addView(Ui.text(this, "热门：", 12, Ui.TXT2));
        for (int i = 0; i < 7; i++) {
            final String kw = HOT[i];
            TextView t = Ui.text(this, kw, 12, Ui.TXT2);
            t.setPadding(Ui.dp(12), Ui.dp(5), Ui.dp(12), Ui.dp(5));
            Ui.focusable(t);
            Ui.addFocusGlow(t,
                    Ui.roundStroke(Color.parseColor("#161920"), 8, Color.parseColor("#282D38"), 1),
                    Ui.round(Ui.PINK, 8));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    input.setText(kw);
                    doSearch(kw);
                }
            });
            LinearLayout.LayoutParams tlp = Ui.lpL(-2, -2);
            tlp.rightMargin = Ui.dp(8);
            hotRow.addView(t, tlp);
        }

        FrameLayout content = new FrameLayout(this);
        LinearLayout.LayoutParams clp = Ui.lpL(-1, 0, 1f);
        clp.topMargin = Ui.dp(10);
        page.addView(content, clp);

        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        gridBox = Ui.vbox(this);
        gridBox.setPadding(0, Ui.dp(4), 0, Ui.dp(40));
        sv.addView(gridBox, new FrameLayout.LayoutParams(-1, -2));
        content.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        tvEmpty = Ui.text(this, "输入关键词后按 OK 搜索\n也可以点上面的热门词", 15, Ui.TXT2);
        tvEmpty.setGravity(Gravity.CENTER);
        content.addView(tvEmpty, new FrameLayout.LayoutParams(-1, -1));

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(48), Ui.dp(48));
        plp.gravity = Gravity.CENTER;
        content.addView(loading, plp);

        setContentView(root);
        input.requestFocus();
    }

    private void hideIme() {
        try {
            InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            im.hideSoftInputFromWindow(input.getWindowToken(), 0);
        } catch (Throwable ignored) {
        }
    }

    private void doSearch(String kw) {
        kw = kw == null ? "" : kw.trim();
        if (kw.length() == 0) {
            Ui.toast(this, "请输入关键词");
            return;
        }
        if (busy) return;
        curKw = kw;
        input.setText(kw);
        load(1);
    }

    private void load(int page) {
        busy = true;
        curPage = page;
        loading.setVisibility(View.VISIBLE);
        if (page == 1) {
            gridBox.removeAllViews();
            tvEmpty.setVisibility(View.GONE);
            sv.scrollTo(0, 0);
        }
        final String kw = curKw;
        Ui.inBg(new Runnable() {
            @Override
            public void run() {
                Api.Page pg = null;
                String err = null;
                try {
                    pg = Api.search(kw, curPage);
                    if (pg.items.isEmpty()) {
                        // 换镜像再试一次
                        pg = Api.search(kw, curPage);
                    }
                } catch (Throwable t) {
                    err = t.getClass().getSimpleName() + ": " + t.getMessage();
                }
                final Api.Page f = pg;
                final String fe = err;
                Ui.runOnUi(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        loading.setVisibility(View.GONE);
                        if (f == null) {
                            tvEmpty.setText("搜索失败：" + fe);
                            tvEmpty.setVisibility(View.VISIBLE);
                            return;
                        }
                        pageCount = f.pageCount;
                        if (f.items.isEmpty()) {
                            tvEmpty.setText("没有找到「" + curKw + "」\n换个名字试试");
                            tvEmpty.setVisibility(View.VISIBLE);
                            return;
                        }
                        render(f.items, curPage < pageCount);
                    }
                });
            }
        });
    }

    private static final int COLS = 5;

    private void render(List<Api.Item> items, boolean more) {
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % COLS == 0) {
                row = Ui.hbox(this);
                row.setGravity(Gravity.TOP);
                LinearLayout.LayoutParams rlp = Ui.lpL(-1, -2);
                rlp.topMargin = Ui.dp(8);
                gridBox.addView(row, rlp);
            }
            row.addView(makeCard(items.get(i)), Ui.lpL(0, -2, 1f));
            ((LinearLayout.LayoutParams) row.getChildAt(row.getChildCount() - 1).getLayoutParams())
                    .rightMargin = Ui.dp(12);
        }
        if (row != null) {
            int filled = items.size() % COLS;
            if (filled != 0) {
                for (int i = filled; i < COLS; i++) row.addView(new View(this), Ui.lpL(0, 1, 1f));
            }
        }
        if (more) {
            TextView moreBtn = Ui.text(this, "▼  加载下一页", 15, Ui.TXT);
            moreBtn.setGravity(Gravity.CENTER);
            Ui.focusable(moreBtn);
            Ui.addFocusGlow(moreBtn,
                    Ui.roundStroke(Color.parseColor("#161920"), 12, Color.parseColor("#2A2F3B"), 1),
                    Ui.roundStroke(Ui.PINK, 12, Ui.PINK, 1));
            moreBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    load(curPage + 1);
                }
            });
            LinearLayout.LayoutParams mlp = Ui.lpL(-1, Ui.dp(52));
            mlp.topMargin = Ui.dp(18);
            gridBox.addView(moreBtn, mlp);
        }
    }

    private View makeCard(final Api.Item it) {
        LinearLayout box = Ui.vbox(this);
        Ui.focusable(box);
        Ui.addFocusGlow(box, Ui.round(Color.parseColor("#14171E"), 12),
                Ui.roundStroke(Color.parseColor("#241820"), 12, Ui.PINK, 2));
        box.setPadding(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(8));

        FrameLayout cb = new FrameLayout(this);
        cb.setBackgroundDrawable(Ui.round(Color.parseColor("#0A0C10"), 9));
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cb.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        Ui.loadImage(iv, it.pic);

        int w = getResources().getDisplayMetrics().widthPixels;
        int cardW = (w - Ui.dp(56) - Ui.dp(12) * (COLS - 1)) / COLS - Ui.dp(12);
        LinearLayout.LayoutParams clp = Ui.lpL(-1, (int) (cardW * 1.42f));
        box.addView(cb, clp);

        TextView nm = Ui.text(this, it.name, 13, Ui.TXT);
        nm.setMaxLines(1);
        nm.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = Ui.lpL(-1, -2);
        nlp.topMargin = Ui.dp(7);
        box.addView(nm, nlp);

        TextView sub = Ui.text(this, it.remark != null && it.remark.length() > 0 ? it.remark : it.typeName, 11, Ui.TXT2);
        sub.setMaxLines(1);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(sub);

        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(SearchActivity.this, DetailActivity.class);
                i.putExtra("item", it);
                startActivity(i);
            }
        });
        return box;
    }
}
