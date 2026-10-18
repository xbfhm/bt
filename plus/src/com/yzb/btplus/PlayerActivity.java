package com.yzb.btplus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
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
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;

import java.util.ArrayList;

/** 播放页：优先原生播放器播 m3u8，失败自动切 H5 播放器；支持遥控器快进快退与自动连播 */
public class PlayerActivity extends Activity {

    private String title = "";
    private ArrayList<String> names;
    private ArrayList<String> urls;
    private int index = 0;

    private FrameLayout root;
    private VideoView video;
    private WebView web;
    private ProgressBar loading;
    private TextView tvTip;
    private TextView tvHud;
    private LinearLayout hud;
    private Handler ui = new Handler(Looper.getMainLooper());
    private boolean triedWeb = false;
    private boolean playing = false;
    private int retry = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.BLACK));

        Intent it = getIntent();
        title = it.getStringExtra("title");
        if (title == null) title = "";
        names = it.getStringArrayListExtra("names");
        urls = it.getStringArrayListExtra("urls");
        index = it.getIntExtra("index", 0);
        String url = it.getStringExtra("url");

        if (urls == null) {
            urls = new ArrayList<String>();
            names = new ArrayList<String>();
        }
        if (urls.isEmpty() && url != null) {
            urls.add(url);
            names.add(title);
            index = 0;
        }
        if (index < 0 || index >= urls.size()) index = 0;

        build();
        play(index);
    }

    private void build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        video = new VideoView(this);
        video.setBackgroundColor(Color.BLACK);
        root.addView(video, new FrameLayout.LayoutParams(-1, -1));

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setVisibility(View.GONE);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 5.1.1; TV) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/78.0 Mobile Safari/537.36");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        loading = new ProgressBar(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(Ui.dp(52), Ui.dp(52));
        plp.gravity = Gravity.CENTER;
        root.addView(loading, plp);

        tvTip = Ui.text(this, "", 14, Ui.TXT2);
        tvTip.setGravity(Gravity.CENTER);
        tvTip.setVisibility(View.GONE);
        root.addView(tvTip, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        // 顶部标题（3 秒后淡出）
        tvHud = Ui.text(this, "", 15, Color.WHITE);
        tvHud.setPadding(Ui.dp(18), Ui.dp(10), Ui.dp(18), Ui.dp(10));
        tvHud.setBackgroundDrawable(Ui.round(Color.parseColor("#99000000"), 8));
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(-2, -2);
        hlp.gravity = Gravity.TOP | Gravity.LEFT;
        hlp.leftMargin = Ui.dp(24);
        hlp.topMargin = Ui.dp(18);
        root.addView(tvHud, hlp);

        hud = Ui.hbox(this);
        hud.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(14), Ui.dp(8));
        hud.setBackgroundDrawable(Ui.round(Color.parseColor("#99000000"), 8));
        hud.setVisibility(View.GONE);
        FrameLayout.LayoutParams hudlp = new FrameLayout.LayoutParams(-2, -2);
        hudlp.gravity = Gravity.TOP | Gravity.RIGHT;
        hudlp.rightMargin = Ui.dp(24);
        hudlp.topMargin = Ui.dp(18);
        root.addView(hud, hudlp);

        setContentView(root);

        video.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                loading.setVisibility(View.GONE);
                tvTip.setVisibility(View.GONE);
                playing = true;
                retry = 0;
                video.start();
                updateHud();
            }
        });
        video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                playing = false;
                onPlayFailed("错误码 " + what + "/" + extra);
                return true;
            }
        });
        video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                playing = false;
                autoNext();
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

    private void play(int idx) {
        index = idx;
        triedWeb = false;
        playing = false;
        loading.setVisibility(View.VISIBLE);
        tvTip.setVisibility(View.GONE);
        web.setVisibility(View.GONE);
        video.setVisibility(View.VISIBLE);
        updateHud();

        String url = urls.get(index);
        try {
            video.setVideoURI(Uri.parse(url));
            video.requestFocus();
            video.start();
        } catch (Throwable t) {
            onPlayFailed(t.getMessage());
        }
    }

    private void onPlayFailed(String why) {
        loading.setVisibility(View.GONE);
        if (!triedWeb) {
            triedWeb = true;
            retry++;
            if (retry <= 2) {
                // 先重试一次（很多 m3u8 首片偶发失败）
                ui.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        loading.setVisibility(View.VISIBLE);
                        try {
                            String url = urls.get(index);
                            video.setVideoURI(Uri.parse(url));
                            video.start();
                        } catch (Throwable ignored) {
                        }
                    }
                }, 900);
                return;
            }
            openWebPlayer();
            return;
        }
        tvTip.setText("这一集暂时无法播放\n" + (why == null ? "" : why) + "\n按 OK 换下一集，按返回退出");
        tvTip.setVisibility(View.VISIBLE);
    }

    private void openWebPlayer() {
        video.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        loading.setVisibility(View.VISIBLE);
        String url = urls.get(index);
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;background:#000;overflow:hidden}"
                + "video{width:100vw;height:100vh;object-fit:contain}</style></head>"
                + "<body><video id='v' src='" + url + "' controls autoplay playsinline "
                + "x5-playsinline webkit-playsinline></video>"
                + "<script>var v=document.getElementById('v');"
                + "v.addEventListener('error',function(){document.title='ERR';});"
                + "v.play().catch(function(){});</script></body></html>";
        web.loadDataWithBaseURL("https://xbfhm.xyz/", html, "text/html", "UTF-8", null);
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                loading.setVisibility(View.GONE);
            }
        }, 3500);
    }

    private void updateHud() {
        String n = (names != null && index < names.size()) ? names.get(index) : "";
        tvHud.setText(title + (n.length() > 0 ? "   " + n : "")
                + (urls.size() > 1 ? "   [" + (index + 1) + "/" + urls.size() + "]" : ""));
        hud.setVisibility(View.VISIBLE);
        hud.removeAllViews();
        hud.addView(Ui.text(this, "←→ 快进退   OK 暂停   ↑↓ 换集   BACK 退出", 12, Ui.TXT2));
        ui.removeCallbacks(hideHud);
        ui.postDelayed(hideHud, 6000);
    }

    private final Runnable hideHud = new Runnable() {
        @Override
        public void run() {
            hud.setVisibility(View.GONE);
        }
    };

    private void autoNext() {
        if (index + 1 < urls.size()) {
            Ui.toast(this, "自动播放下一集");
            play(index + 1);
        } else {
            tvTip.setText("已播放完最后一集\n按返回键退出");
            tvTip.setVisibility(View.VISIBLE);
        }
    }

    private void seekBy(int ms) {
        if (video.getVisibility() != View.VISIBLE) return;
        int pos = video.getCurrentPosition();
        int dur = 0;
        try {
            dur = video.getDuration();
        } catch (Throwable ignored) {
        }
        int np = pos + ms;
        if (np < 0) np = 0;
        if (dur > 0 && np > dur) np = dur;
        video.seekTo(np);
        tvHud.setText(format(np) + " / " + format(dur));
        hud.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideHud);
        ui.postDelayed(hideHud, 3000);
    }

    private static String format(int ms) {
        if (ms < 0) ms = 0;
        int s = ms / 1000;
        int m = s / 60;
        int ss = s % 60;
        int h = m / 60;
        m = m % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, ss);
        return String.format("%02d:%02d", m, ss);
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                seekBy(-10000);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                seekBy(10000);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (index > 0) play(index - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (index + 1 < urls.size()) play(index + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (video.getVisibility() == View.VISIBLE) {
                    if (video.isPlaying()) {
                        video.pause();
                        tvHud.setText("已暂停");
                    } else {
                        video.start();
                        tvHud.setText(title);
                    }
                    hud.setVisibility(View.VISIBLE);
                    ui.removeCallbacks(hideHud);
                    ui.postDelayed(hideHud, 3000);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (video.isPlaying()) video.pause();
                else video.start();
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekBy(30000);
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekBy(-30000);
                return true;
            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;
            default:
                break;
        }
        return super.onKeyDown(code, e);
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
        try {
            web.loadUrl("about:blank");
            web.destroy();
        } catch (Throwable ignored) {
        }
    }
}
