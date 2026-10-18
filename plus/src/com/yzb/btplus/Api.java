package com.yzb.btplus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** 免费影视接口客户端（非凡资源 cms 接口，m3u8 直链） */
public class Api {

    public static final String[] HOSTS = {
            "https://api.ffzyapi.com/api.php/provide/vod/",
            "https://cj.ffzyapi.com/api.php/provide/vod/",
            "https://api.ffzyapi.org/api.php/provide/vod/"
    };
    private static int hostIdx = 0;

    /** 分类 */
    public static final String[][] CATS = {
            {"日韩动漫", "30"},
            {"国产动漫", "29"},
            {"欧美动漫", "31"},
            {"国产剧", "13"},
            {"日剧", "22"},
            {"美剧", "16"},
            {"韩剧", "15"},
            {"港台剧", "14"},
            {"电影", "1"},
            {"综艺", "25"}
    };

    public static class Item implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public String id = "";
        public String name = "";
        public String pic = "";
        public String remark = "";
        public String typeName = "";
        public String year = "";
        public String area = "";
        public List<String[]> eps = new ArrayList<String[]>();

        public String firstUrl() {
            if (eps.isEmpty()) return "";
            for (String[] e : eps) {
                if (e[1] != null && e[1].length() > 5) return e[1];
            }
            return "";
        }
    }

    public static class Page {
        public List<Item> items = new ArrayList<Item>();
        public int page = 1;
        public int pageCount = 1;
        public int total = 0;
    }

    public static String get(String url) throws Exception {
        return get(url, null);
    }

    public static String get(String url, String cookie) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 5.1.1) AppleWebKit/537.36 Chrome/80 Mobile Safari/537.36");
            c.setRequestProperty("Accept", "application/json,text/plain,*/*");
            c.setRequestProperty("Accept-Encoding", "identity");
            if (cookie != null) {
                c.setRequestProperty("Cookie", cookie);
                c.setRequestProperty("Referer", "https://www.bilibili.com/");
                c.setRequestProperty("Origin", "https://www.bilibili.com");
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            return new String(bo.toByteArray(), "UTF-8");
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** 依次尝试多个镜像域名 */
    public static String getWithFallback(String pathAndQuery) throws Exception {
        Exception last = null;
        for (int i = 0; i < HOSTS.length; i++) {
            int idx = (hostIdx + i) % HOSTS.length;
            try {
                String r = get(HOSTS[idx] + pathAndQuery);
                if (r != null && r.length() > 20) {
                    hostIdx = idx;
                    return r;
                }
            } catch (Exception e) {
                last = e;
            }
        }
        if (last != null) throw last;
        throw new Exception("no host available");
    }

    public static Page list(String typeId, int page) throws Exception {
        String q = "?ac=videolist&pg=" + page + "&pagesize=20";
        if (typeId != null && typeId.length() > 0) q += "&t=" + typeId;
        return parse(getWithFallback(q));
    }

    public static Page search(String kw, int page) throws Exception {
        String q = "?ac=videolist&wd=" + URLEncoder.encode(kw, "UTF-8") + "&pg=" + page + "&pagesize=20";
        return parse(getWithFallback(q));
    }

    public static Page parse(String json) throws Exception {
        Page p = new Page();
        JSONObject o = new JSONObject(json);
        p.page = o.optInt("page", 1);
        p.pageCount = o.optInt("pagecount", 1);
        p.total = o.optInt("total", 0);
        JSONArray arr = o.optJSONArray("list");
        if (arr == null) return p;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject j = arr.optJSONObject(i);
            if (j == null) continue;
            Item it = new Item();
            it.id = j.optString("vod_id", "");
            it.name = j.optString("vod_name", "").trim();
            it.pic = j.optString("vod_pic", "");
            it.remark = j.optString("vod_remarks", "");
            it.typeName = j.optString("type_name", "");
            it.year = j.optString("vod_year", "");
            it.area = j.optString("vod_area", "");
            parsePlayUrl(it, j.optString("vod_play_url", ""));
            if (it.name.length() > 0) p.items.add(it);
        }
        return p;
    }

    /** vod_play_url 形如：第01集$url#第02集$url 或 线路1$...#...$$$线路2$... */
    private static void parsePlayUrl(Item it, String raw) {
        if (raw == null || raw.length() == 0) return;
        String line = raw;
        int sep = raw.indexOf("$$$");
        if (sep > 0) line = raw.substring(0, sep);
        String[] parts = line.split("#");
        for (String s : parts) {
            if (s == null) continue;
            s = s.trim();
            if (s.length() == 0) continue;
            int d = s.indexOf('$');
            String nm, url;
            if (d > 0) {
                nm = s.substring(0, d);
                url = s.substring(d + 1);
            } else {
                nm = "播放";
                url = s;
            }
            if (url.startsWith("http")) it.eps.add(new String[]{nm, url});
        }
    }
}
