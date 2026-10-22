package com.yzb.btplus;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 极简日志：写入 App 私有外部目录，同时在内存保留最近 N 行，
 * 供 LogActivity 在电视上直接显示（电视没有 logcat 可看）。
 */
public final class Log {

    private static final int MAX_CHARS = 60000;
    private static final StringBuilder BUF = new StringBuilder();
    private static final SimpleDateFormat FMT =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);

    private static File logFile;
    private static String dirPath = "";

    private Log() {
    }

    public static synchronized void init(Context c) {
        if (logFile != null || c == null) return;
        try {
            File dir = null;
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                dir = c.getExternalFilesDir(null);
            }
            if (dir == null) {
                dir = c.getFilesDir();
            }
            if (dir != null) {
                if (!dir.exists()) dir.mkdirs();
                logFile = new File(dir, "btplus.log");
                dirPath = dir.getAbsolutePath();
            }
        } catch (Throwable ignored) {
        }
        i("Log", "logger started, sdk=" + Build.VERSION.SDK_INT
                + " model=" + Build.MANUFACTURER + " " + Build.MODEL
                + " file=" + (logFile == null ? "(none)" : logFile.getAbsolutePath()));
    }

    public static String dir() {
        return dirPath;
    }

    public static String deviceInfo() {
        return "sdk=" + Build.VERSION.SDK_INT
                + "  " + Build.MANUFACTURER + " " + Build.MODEL
                + "\n" + Build.VERSION.RELEASE;
    }

    public static void i(String tag, String msg) {
        write("I", tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        write("E", tag, msg, t);
    }

    private static synchronized void write(String level, String tag, String msg, Throwable t) {
        StringBuilder line = new StringBuilder();
        line.append(FMT.format(new Date())).append(' ')
                .append(level).append('/').append(tag).append(": ")
                .append(msg == null ? "" : msg);
        if (t != null) {
            line.append('\n').append(stack(t));
        }
        line.append('\n');

        BUF.append(line);
        if (BUF.length() > MAX_CHARS) {
            BUF.delete(0, BUF.length() - MAX_CHARS);
        }

        if (logFile != null) {
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(logFile, true);
                out.write(line.toString().getBytes("UTF-8"));
                out.flush();
            } catch (Throwable ignored) {
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    public static String stack(Throwable t) {
        if (t == null) return "";
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            pw.flush();
            return sw.toString();
        } catch (Throwable x) {
            return String.valueOf(t);
        }
    }

    public static synchronized String dump() {
        return BUF.toString();
    }

    /** 捕获未处理异常：写盘 + 交给系统默认处理（仍然会闪退，但留下证据） */
    public static void installCrashHandler(final Context c) {
        final Thread.UncaughtExceptionHandler prev =
                Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread th, Throwable ex) {
                try {
                    init(c);
                    write("E", "CRASH", "thread=" + th.getName(), ex);
                } catch (Throwable ignored) {
                }
                if (prev != null) {
                    prev.uncaughtException(th, ex);
                }
            }
        });
    }
}
