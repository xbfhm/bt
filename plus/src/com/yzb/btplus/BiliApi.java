package com.yzb.btplus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * B 站公开接口（无需登录）
 * 注意：x/web-interface/view 在机房 IP 上会被风控返回 HTML，
 *      所以拿 cid 一律走 x/player/pagelist，播放地址走 playurl(bvid+cid)。
 */
public class BiliApi {

    private static String cookie = null;

    private static synchronized String cookie() {
        if (cookie == null) {
            Random r = new Random();
            StringBuilder a = new StringBuilder();
            String hex = "0123456789abcdef";
            for (int i = 0; i < 32; i++) a.append(hex.charAt(r.nextInt(16)));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 32; i++) b.append(hex.charAt(r.nextInt(16)));
            cookie = "buvid3=" + a + "infoc; buvid4=" + b + "-1";
        }
        return cookie;
    }

    private static String get(String url) throws Exception {
        return Api.get(url, cookie());
    }

    public static class Video implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        public String bvid = "";
        public String cid = "";
        public String title = "";
        public String pic = "";
        public String owner = "";
        public String desc = "";
        public String durText = "";
        public String playUrl = "";
    }

    private static String durText(int sec) {
        if (sec <= 0) return "";
        int m = sec / 60, s = sec % 60;
        if (m >= 60) return String.format("%d:%02d:%02d", m / 60, m % 60, s);
        return String.format("%d:%02d", m, s);
    }

    /** 推荐流：多接口兜底 */
    public static List<Video> feed() {
        List<Video> out = new ArrayList<Video>();
        String[] urls = {
                "https://api.bilibili.com/x/web-interface/index/top/feed/rcmd?ps=20&fresh_type=4&version=1&feed_version=V8",
                "https://api.bilibili.com/x/web-interface/ranking/v2?rid=0&type=all",
                "https://api.bilibili.com/x/web-interface/dynamic/region?rid=1&ps=20&pn=1",
                "https://api.bilibili.com/x/web-interface/newlist?rid=1&ps=20&pn=1"
        };
        for (String u : urls) {
            try {
                String r = get(u);
                if (r == null || !r.startsWith("{")) continue;
                JSONObject o = new JSONObject(r);
                if (o.optInt("code", -1) != 0) continue;
                JSONObject data = o.optJSONObject("data");
                if (data == null) continue;
                JSONArray arr = data.optJSONArray("item");
                if (arr == null) arr = data.optJSONArray("list");
                if (arr == null) arr = data.optJSONArray("archives");
                if (arr == null) continue;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject j = arr.optJSONObject(i);
                    if (j == null) continue;
                    Video v = parse(j);
                    if (v != null) out.add(v);
                }
                if (!out.isEmpty()) return out;
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    private static Video parse(JSONObject j) {
        try {
            String bvid = j.optString("bvid", "");
            if (bvid.length() < 5) return null;
            Video v = new Video();
            v.bvid = bvid;
            v.title = j.optString("title", "");
            String p = j.optString("pic", "");
            if (p.startsWith("//")) p = "https:" + p;
            else if (p.startsWith("http://")) p = "https://" + p.substring(7);
            v.pic = p;
            v.desc = j.optString("desc", "");
            v.durText = durText(j.optInt("duration", 0));
            JSONObject owner = j.optJSONObject("owner");
            if (owner != null) v.owner = owner.optString("name", "");
            if (v.owner.length() == 0) v.owner = j.optString("author", "");
            // 有些接口直接带 cid
            String c = j.optString("cid", "");
            if (c.length() > 0 && !"0".equals(c)) v.cid = c;
            if (v.title.length() == 0) return null;
            return v;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 用 pagelist 拿 cid（view 接口在此环境被风控） */
    public static String cid(Video v) {
        if (v.cid != null && v.cid.length() > 0 && !"0".equals(v.cid)) return v.cid;
        try {
            String r = get("https://api.bilibili.com/x/player/pagelist?bvid=" + v.bvid);
            if (r == null || !r.startsWith("{")) return null;
            JSONObject o = new JSONObject(r);
            if (o.optInt("code", -1) != 0) return null;
            JSONArray a = o.optJSONArray("data");
            if (a == null || a.length() == 0) return null;
            JSONObject p0 = a.optJSONObject(0);
            if (p0 == null) return null;
            String c = p0.optString("cid", "");
            if (c.length() == 0) return null;
            v.cid = c;
            if (v.durText == null || v.durText.length() == 0) {
                v.durText = durText(p0.optInt("duration", 0));
            }
            return c;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 取 mp4 直链：platform=html5 才有 durl（无需登录，无需 WBI） */
    public static String playUrl(Video v) {
        if (v.playUrl != null && v.playUrl.length() > 8) return v.playUrl;
        String cid = cid(v);
        if (cid == null) return null;
        // 从高到低尝试画质
        String[] qns = {"64", "32", "16"};
        for (String qn : qns) {
            try {
                String u = "https://api.bilibili.com/x/player/playurl?bvid=" + v.bvid
                        + "&cid=" + cid + "&qn=" + qn
                        + "&fnval=0&fnver=0&fourk=0&platform=html5&high_quality=1";
                String r = get(u);
                if (r == null || !r.startsWith("{")) continue;
                JSONObject o = new JSONObject(r);
                if (o.optInt("code", -1) != 0) continue;
                JSONObject d = o.optJSONObject("data");
                if (d == null) continue;
                JSONArray durl = d.optJSONArray("durl");
                if (durl == null || durl.length() == 0) continue;
                JSONObject d0 = durl.optJSONObject(0);
                if (d0 == null) continue;
                String url = d0.optString("url", "");
                if (url.length() > 10) {
                    v.playUrl = url;
                    return url;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
