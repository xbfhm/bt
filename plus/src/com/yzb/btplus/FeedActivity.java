package com.yzb.btplus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 刷视频页：竖屏/全屏卡片式短视频流（类似抖音 / 云视听小电视推荐）
 * 遥控器 ↑↓ 切换，OK 暂停，左右为切换视频，BACK 退出
 */
public class FeedActivity extends Activity {

    private static final int PREFETCH = 4;

    private final List<BiliApi.Video> queue = new ArrayList<BiliApi.Video>();
    private int idx = -1;
    private boolean busy = false;
    private boolean exhausted = false;

    private FrameLayout root;
    private ImageView cover;
    private VideoView video;
    private ProgressBar loading;
    private LinearLayout infoBox;
    private TextView tvTitle, tvOwner, tvCount, tvHint;
    private View likeBtn;
    private TextView tvLike;
    private LinearLayout btnBar;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean paused = false;
    private boolean liked = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        build();
        loadMore();
    }

    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // 背景封面（模糊感：用半透明黑遮罩代替模糊）
        cover = new ImageView(this);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(cover, new FrameLayout.LayoutParams(-1, -1));

        View dim = new View(this);
        dim.setBackgroundColor(Color.parseColor("#88000000"));
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));

        video = new VideoView(this);
        video.setBackgroundColor(Color.TRANSPARENT);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));

        loading = new ProgressBar(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(54), Ui.dp(54));
        plp.gravity = Gravity.CENTER;
        root.addView(loading, plp);

        // 顶部栏
        LinearLayout top = Ui.hbox(this);
        top.setPadding(Ui.dp(26), Ui.dp(16), Ui.dp(26), 0);
        TextView tab1 = pill("推荐", true);
        TextView tab2 = pill("免费影视", false);
        tab2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(FeedActivity.this, PlusActivity.class));
                finish();
            }
        });
        top.addView(tab1);
        LinearLayout.LayoutParams t2 = Ui.lpL(-2, Ui.dp(36));
        t2.leftMargin = Ui.dp(10);
        top.addView(tab2, t2);

        View sp = new View(this);
        top.addView(sp, Ui.lpL(0, 1, 1f));
        TextView close = pill("返回", false);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(close);
        root.addView(top, new FrameLayout.LayoutParams(-1, -2));

        // 底部信息
        infoBox = Ui.vbox(this);
        infoBox.setPadding(Ui.dp(30), Ui.dp(16), Ui.dp(30), Ui.dp(22));
        infoBox.setBackgroundDrawable(bottomGradient());

        tvTitle = Ui.text(this, "", 22, Color.WHITE);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setMaxLines(2);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        infoBox.addView(tvTitle);

        LinearLayout metaRow = Ui.hbox(this);
        LinearLayout.LayoutParams mrlp = Ui.lpL(-1, -2);
        mrlp.topMargin = Ui.dp(8);
        infoBox.addView(metaRow, mrlp);

        tvOwner = Ui.text(this, "", 13, Color.parseColor("#D8DBE5"));
        metaRow.addView(tvOwner);
        tvCount = Ui.text(this, "", 13, Ui.TXT2);
        LinearLayout.LayoutParams clp = Ui.lpL(-2, -2);
        clp.leftMargin = Ui.dp(14);
        metaRow.addView(tvCount, clp);

        btnBar = Ui.hbox(this);
        LinearLayout.LayoutParams blp = Ui.lpL(-1, -2);
        blp.topMargin = Ui.dp(14);
        infoBox.addView(btnBar, blp);

        likeBtn = makeSmallBtn("♡ 点赞");
        tvLike = (TextView) likeBtn;
        likeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                liked = !liked;
                tvLike.setText(liked ? "♥ 已赞" : "♡ 点赞");
                tvLike.setTextColor(liked ? Ui.PINK : Ui.TXT);
            }
        });
        btnBar.addView(likeBtn);

        View openBtn = makeSmallBtn("在 B 站打开");
        openBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BiliApi.Video v0 = cur();
                if (v0 == null) return;
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.bilibili.com/video/" + v0.bvid));
                    startActivity(i);
                } catch (Throwable t) {
                    Ui.toast(FeedActivity.this, "没有可用的浏览器");
                }
            }
        });
        LinearLayout.LayoutParams oblp = Ui.lpL(-2, Ui.dp(38));
        oblp.leftMargin = Ui.dp(10);
        btnBar.addView(openBtn, oblp);

        View nextBtn = makeSmallBtn("下一个  ↓");
        nextBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                next();
            }
        });
        LinearLayout.LayoutParams nlp = Ui.lpL(-2, Ui.dp(38));
        nlp.leftMargin = Ui.dp(10);
        btnBar.addView(nextBtn, nlp);

        tvHint = Ui.text(this, "↑↓ 切换视频    OK 暂停    BACK 退出", 12, Ui.TXT2);
        LinearLayout.LayoutParams hlp = Ui.lpL(-1, -2);
        hlp.topMargin = Ui.dp(12);
        infoBox.addView(tvHint, hlp);

        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(-1, -2);
        ilp.gravity = Gravity.BOTTOM;
        root.addView(infoBox, ilp);

        setContentView(root);

        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                loading.setVisibility(View.GONE);
                video.start();
                paused = false;
            }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                loading.setVisibility(View.GONE);
                Ui.toast(FeedActivity.this, "这个视频无法播放，自动跳过");
                ui.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        next();
                    }
                }, 600);
                return true;
            }
        });
        video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                next();
            }
        });
        video.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) loading.setVisibility(View.VISIBLE);
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) loading.setVisibility(View.GONE);
                return false;
            }
        });
    }

    private android.graphics.drawable.Drawable bottomGradient() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Color.parseColor("#EE000000"), Color.parseColor("#00000000")});
        g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        return g;
    }

    private TextView pill(String s, boolean sel) {
        TextView t = Ui.text(this, s, 13, sel ? Color.WHITE : Ui.TXT2);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(20), 0, Ui.dp(20), 0);
        Ui.focusable(t);
        Ui.addFocusGlow(t,
                sel ? Ui.round(Ui.PINK, 18) : Ui.roundStroke(Color.parseColor("#331A1D26"), 18,
                        Color.parseColor("#55FFFFFF"), 1),
                Ui.round(Ui.PINK, 18));
        return t;
    }

    private View makeSmallBtn(String s) {
        TextView t = Ui.text(this, s, 13, Ui.TXT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(16), 0, Ui.dp(16), 0);
        Ui.focusable(t);
        Ui.addFocusGlow(t,
                Ui.roundStroke(Color.parseColor("#55202430"), 9, Color.parseColor("#66FFFFFF"), 1),
                Ui.round(Ui.PINK, 9));
        return t;
    }

    private BiliApi.Video cur() {
        if (idx >= 0 && idx < queue.size()) return queue.get(idx);
        return null;
    }

    /** 拉取一批推荐 */
    private void loadMore() {
        if (busy || exhausted) return;
        busy = true;
        final int want = queue.size() + 20;
        Ui.inBg(new Runnable() {
            @Override
            public void run() {
                List<BiliApi.Video> got = BiliApi.feed();
                final List<BiliApi.Video> add = new ArrayList<BiliApi.Video>();
                for (BiliApi.Video v : got) {
                    if (v == null || v.bvid.length() == 0) continue;
                    boolean dup = false;
                    for (BiliApi.Video e : queue) {
                        if (e.bvid.equals(v.bvid)) {
                            dup = true;
                            break;
                        }
                    }
                    if (!dup) add.add(v);
                }
                Ui.runOnUi(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (add.isEmpty()) {
                            exhausted = true;
                            if (queue.isEmpty()) {
                                Ui.toast(FeedActivity.this, "推荐流加载失败，请检查网络");
                            }
                            return;
                        }
                        queue.addAll(add);
                        if (idx < 0) {
                            idx = 0;
                            show(0);
                        }
                    }
                });
            }
        });
    }

    private void next() {
        if (idx + 1 < queue.size()) {
            show(idx + 1);
            if (queue.size() - (idx + 1) <= PREFETCH) loadMore();
        } else if (!exhausted) {
            loadMore();
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (idx + 1 < queue.size()) show(idx + 1);
                    else Ui.toast(FeedActivity.this, "已经到底啦");
                }
            }, 800);
        } else {
            Ui.toast(this, "已经到底啦");
        }
    }

    private void prev() {
        if (idx > 0) show(idx - 1);
    }

    private void show(int i) {
        if (i < 0 || i >= queue.size()) return;
        idx = i;
        final BiliApi.Video v = queue.get(i);
        paused = false;
        liked = false;
        tvLike.setText("♡ 点赞");
        tvLike.setTextColor(Ui.TXT);

        tvTitle.setText(v.title);
        tvOwner.setText(v.owner);
        tvCount.setText("");
        cover.setImageDrawable(null);
        Ui.loadImage(cover, v.pic);
        loading.setVisibility(View.VISIBLE);
        video.setVisibility(View.VISIBLE);

        // 进场动画
        AlphaAnimation a = new AlphaAnimation(0.2f, 1f);
        a.setDuration(260);
        infoBox.startAnimation(a);

        ui.post(new Runnable() {
            @Override
            public void run() {
                try {
                    video.stopPlayback();
                } catch (Throwable ignored) {
                }
            }
        });

        Ui.inBg(new Runnable() {
            @Override
            public void run() {
                String url = BiliApi.playUrl(v);
                final String f = url;
                Ui.runOnUi(new Runnable() {
                    @Override
                    public void run() {
                        if (queue.get(idx) != v) return; // 已切换
                        if (f == null) {
                            loading.setVisibility(View.GONE);
                            Ui.toast(FeedActivity.this, "获取播放地址失败，跳过");
                            ui.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    next();
                                }
                            }, 600);
                            return;
                        }
                        try {
                            video.setVideoURI(Uri.parse(f));
                            video.requestFocus();
                            video.start();
                        } catch (Throwable t) {
                            loading.setVisibility(View.GONE);
                        }
                    }
                });
            }
        });
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_PAGE_DOWN:
                next();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_PAGE_UP:
                prev();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                next();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                try {
                    if (video.isPlaying()) {
                        video.pause();
                        paused = true;
                        Ui.toast(this, "已暂停");
                    } else {
                        video.start();
                        paused = false;
                    }
                } catch (Throwable ignored) {
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;
            default:
                return super.onKeyDown(code, e);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (video.isPlaying()) video.pause();
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        try {
            video.stopPlayback();
        } catch (Throwable ignored) {
        }
    }
}
