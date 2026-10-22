package com.yzb.btplus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 免费影视库主页：分类浏览 + 搜索（数据来自免费 m3u8 采集站） */
public class PlusActivity extends Activity {

    private FrameLayout root;
    private LinearLayout gridBox;
    private ProgressBar loading;
    private TextView tvEmpty;
    private LinearLayout tabsBox;
    private HorizontalScrollView tabsScroll;
    private ScrollView gridScroll;
    private int curCat = 0;
    private int curPage = 1;
    private int pageCount = 1;
    private boolean loading2 = false;
    private final List<TextView> tabViews = new ArrayList<TextView>();
    private View searchBtn;
    private TextView pageInfo;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        Log.init(this);
        Log.installCrashHandler(getApplicationContext());
        Log.i("Plus", "onCreate");
        try {
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Ui.BG));
            buildUi();
            loadCat(0, 1);
        } catch (Throwable t) {
            Ui.showFatal(this, t);
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout page = Ui.vbox(this);
        page.setBackgroundColor(Ui.BG);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        // ---------- 顶栏 ----------
        LinearLayout top = Ui.hbox(this);
        top.setPadding(Ui.dp(28), Ui.dp(16), Ui.dp(28), Ui.dp(8));

        TextView title = Ui.text(this, "免费影视库", 24, Ui.TXT);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        top.addView(title);

        TextView badge = Ui.text(this, "  无广告 · 无需会员", 13, Ui.TXT2);
        top.addView(badge);

        View spacer = new View(this);
        top.addView(spacer, Ui.lpL(0, 1, 1f));

        View feedBtn = makeBtn("刷视频");
        feedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PlusActivity.this, FeedActivity.class));
            }
        });
        LinearLayout.LayoutParams flp = Ui.lpL(Ui.dp(96), Ui.dp(40));
        flp.rightMargin = Ui.dp(10);
        top.addView(feedBtn, flp);

        searchBtn = makeBtn("搜索");
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(PlusActivity.this, SearchActivity.class));
            }
        });
        top.addView(searchBtn);

        View backBtn = makeBtn("返回");
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams blp = Ui.lpL(Ui.dp(96), Ui.dp(40));
        blp.leftMargin = Ui.dp(12);
        top.addView(backBtn, blp);

        page.addView(top, Ui.lpL(-1, -2));

        // ---------- 分类横滑 ----------
        tabsScroll = new HorizontalScrollView(this);
        tabsScroll.setHorizontalScrollBarEnabled(false);
        tabsScroll.setPadding(Ui.dp(24), 0, Ui.dp(24), 0);
        tabsBox = Ui.hbox(this);
        tabsScroll.addView(tabsBox, new FrameLayout.LayoutParams(-2, -1));
        page.addView(tabsScroll, Ui.lpL(-1, Ui.dp(60)));

        pageInfo = Ui.text(this, "", 12, Ui.TXT2);
        pageInfo.setPadding(Ui.dp(28), 0, Ui.dp(28), Ui.dp(2));
        page.addView(pageInfo, Ui.lpL(-1, -2));

        for (int i = 0; i < Api.CATS.length; i++) {
            TextView t = Ui.text(this, Api.CATS[i][0], 15, Ui.TXT2);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(22), 0, Ui.dp(22), 0);
            final int idx = i;
            Ui.focusable(t);
            LinearLayout.LayoutParams tlp = Ui.lpL(-2, Ui.dp(42));
            tlp.rightMargin = Ui.dp(10);
            tlp.topMargin = Ui.dp(9);
            tlp.bottomMargin = Ui.dp(9);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (idx != curCat) loadCat(idx, 1);
                }
            });
            t.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int code, KeyEvent e) {
                    if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (code == KeyEvent.KEYCODE_DPAD_RIGHT && idx + 1 < Api.CATS.length) {
                        loadCat(idx + 1, 1);
                        tabViews.get(idx + 1).requestFocus();
                        return true;
                    }
                    if (code == KeyEvent.KEYCODE_DPAD_LEFT && idx > 0) {
                        loadCat(idx - 1, 1);
                        tabViews.get(idx - 1).requestFocus();
                        return true;
                    }
                    return false;
                }
            });
            tabsBox.addView(t, tlp);
            tabViews.add(t);
        }
        paintTabs();

        // ---------- 内容区 ----------
        FrameLayout content = new FrameLayout(this);
        page.addView(content, Ui.lpL(-1, 0, 1f));

        gridScroll = new ScrollView(this);
        gridScroll.setVerticalScrollBarEnabled(false);
        gridBox = Ui.vbox(this);
        gridBox.setPadding(Ui.dp(24), Ui.dp(6), Ui.dp(24), Ui.dp(40));
        gridScroll.addView(gridBox, new FrameLayout.LayoutParams(-1, -2));
        content.addView(gridScroll, new FrameLayout.LayoutParams(-1, -1));

        tvEmpty = Ui.text(this, "", 15, Ui.TXT2);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setVisibility(View.GONE);
        content.addView(tvEmpty, new FrameLayout.LayoutParams(-1, -1));

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(48), Ui.dp(48));
        plp.gravity = Gravity.CENTER;
        content.addView(loading, plp);

        setContentView(root);
    }

    private View makeBtn(String s) {
        TextView t = Ui.text(this, s, 14, Ui.TXT);
        t.setGravity(Gravity.CENTER);
        Ui.focusable(t);
        Ui.addFocusGlow(t,
                Ui.roundStroke(Color.parseColor("#1A1D26"), 10, Color.parseColor("#2E3340"), 1),
                Ui.roundStroke(Ui.PINK, 10, Ui.PINK, 1));
        return t;
    }

    private void paintTabs() {
        for (int i = 0; i < tabViews.size(); i++) {
            TextView t = tabViews.get(i);
            if (i == curCat) {
                t.setTextColor(Color.WHITE);
                t.setBackgroundDrawable(Ui.round(Ui.PINK, 10));
            } else {
                t.setTextColor(Ui.TXT2);
                t.setBackgroundDrawable(Ui.roundStroke(Color.parseColor("#161920"), 10,
                        Color.parseColor("#282D38"), 1));
            }
        }
    }

    private void loadCat(int idx, int page) {
        if (loading2) return;
        loading2 = true;
        curCat = idx;
        curPage = page;
        paintTabs();
        loading.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        if (page == 1) {
            gridBox.removeAllViews();
            gridScroll.scrollTo(0, 0);
        }

        final String typeId = Api.CATS[idx][1];
        Ui.inBg(new Runnable() {
            @Override
            public void run() {
                Api.Page pg = null;
                String err = null;
                try {
                    pg = Api.list(typeId, curPage);
                } catch (Throwable t) {
                    err = t.getClass().getSimpleName() + ": " + t.getMessage();
                }
                final Api.Page fpg = pg;
                final String ferr = err;
                Ui.runOnUi(new Runnable() {
                    @Override
                    public void run() {
                        loading2 = false;
                        loading.setVisibility(View.GONE);
                        if (fpg == null) {
                            Log.e("Plus","list failed " + typeId + " p" + curPage, null);
                            showEmpty("加载失败：" + ferr + "\n\n按【菜单】键重试，或按返回退出");
                            return;
                        }
                        pageCount = fpg.pageCount;
                        showPageInfo();
                        render(fpg.items, curPage < pageCount);
                    }
                });
            }
        });
    }

    private void showPageInfo() {
        if (pageInfo == null) return;
        pageInfo.setText("第 " + curPage + " / " + pageCount + " 页");
    }

    private void showEmpty(String s) {
        tvEmpty.setText(s);
        tvEmpty.setVisibility(View.VISIBLE);
    }

    private static final int COLS = 5;

    private void render(List<Api.Item> items, boolean more) {
        if (items.isEmpty()) {
            showEmpty("这个分类暂时没有内容");
            return;
        }
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % COLS == 0) {
                row = Ui.hbox(this);
                row.setGravity(Gravity.TOP);
                LinearLayout.LayoutParams rlp = Ui.lpL(-1, -2);
                rlp.topMargin = Ui.dp(8);
                gridBox.addView(row, rlp);
            }
            View card = makeCard(items.get(i));
            LinearLayout.LayoutParams clp = Ui.lpL(0, -2, 1f);
            clp.rightMargin = Ui.dp(12);
            row.addView(card, clp);
        }
        // 补齐空位，保证每格等宽
        if (row != null) {
            int filled = items.size() % COLS;
            if (filled != 0) {
                for (int i = filled; i < COLS; i++) {
                    View v = new View(this);
                    row.addView(v, Ui.lpL(0, 1, 1f));
                }
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
                    loadCat(curCat, curPage + 1);
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
        GradientDrawable normal = Ui.round(Color.parseColor("#14171E"), 12);
        GradientDrawable focused = Ui.roundStroke(Color.parseColor("#241820"), 12, Ui.PINK, 2);
        Ui.addFocusGlow(box, normal, focused);
        box.setPadding(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(8));

        FrameLayout coverBox = new FrameLayout(this);
        coverBox.setBackgroundDrawable(Ui.round(Color.parseColor("#0A0C10"), 9));

        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverBox.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        Ui.loadImage(iv, it.pic);

        TextView remark = Ui.text(this, it.remark == null ? "" : it.remark, 10, Color.WHITE);
        remark.setGravity(Gravity.CENTER);
        remark.setBackgroundDrawable(Ui.round(Color.parseColor("#CC000000"), 6));
        remark.setPadding(Ui.dp(6), Ui.dp(2), Ui.dp(6), Ui.dp(2));
        remark.setMaxLines(1);
        remark.setEllipsize(TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(-2, -2);
        rlp.gravity = Gravity.RIGHT | Gravity.BOTTOM;
        rlp.rightMargin = Ui.dp(5);
        rlp.bottomMargin = Ui.dp(5);
        rlp.leftMargin = Ui.dp(5);
        coverBox.addView(remark, rlp);

        LinearLayout.LayoutParams clp = Ui.lpL(-1, 0);
        int w = getResources().getDisplayMetrics().widthPixels;
        int cardW = (w - Ui.dp(48) - Ui.dp(12) * (COLS - 1)) / COLS - Ui.dp(12);
        clp.height = (int) (cardW * 1.42f);
        box.addView(coverBox, clp);

        TextView nm = Ui.text(this, it.name, 13, Ui.TXT);
        nm.setMaxLines(1);
        nm.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = Ui.lpL(-1, -2);
        nlp.topMargin = Ui.dp(7);
        box.addView(nm, nlp);

        TextView sub = Ui.text(this, (it.year != null && it.year.length() > 0 ? it.year : it.typeName), 11, Ui.TXT2);
        sub.setMaxLines(1);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(sub);

        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(PlusActivity.this, DetailActivity.class);
                i.putExtra("item", it);
                startActivity(i);
            }
        });
        return box;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            // 有内容时菜单键=刷新当前分类；空/失败时=重试
            loadCat(curCat, curPage);
            return true;
        }
        if (code == KeyEvent.KEYCODE_SEARCH) {
            startActivity(new Intent(this, SearchActivity.class));
            return true;
        }
        return super.onKeyDown(code, e);
    }
}
