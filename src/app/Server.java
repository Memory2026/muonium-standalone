package app;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.muoniumplayer.core.ncm.api.CloudMusicApi;
import com.muoniumplayer.core.ncm.RequestUtil.RequestAnswer;
import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.ncm.music.Quality;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.utils.Tuple;
import com.sun.net.httpserver.*;
import top.fpsmaster.music.MusicSource;
import top.fpsmaster.music.Track;

import java.io.*;
import java.net.*;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 本地后端：复用剥离出的搜索/取流能力，通过 HTTP 给本地 HTML 界面提供数据与音频代理。
 * 不启动 MC，不依赖任何 MC 界面类。
 */
public class Server {

    public static final int PORT = 18391;
    private static final Gson GSON = new Gson();
    private static final Object lock = new Object();
    private static List<Music> lastResults = new ArrayList<>();
    private static Path webRoot;
    private static Path dataDir;

    public static void main(String[] args) throws Exception {
        CloudMusic.initNCM();

        webRoot = Paths.get(System.getProperty("muonium.web", "C:\\muo\\web"));
        dataDir = Paths.get(System.getProperty("muonium.data", webRoot.resolve("..").resolve("data").toString()));
        Files.createDirectories(dataDir);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        server.createContext("/api/search", Server::handleSearch);
        server.createContext("/api/stream", Server::handleStream);
        server.createContext("/api/hot", Server::handleHot);
        server.createContext("/api/charts", Server::handleCharts);
        server.createContext("/api/chart", Server::handleChartTracks);
        server.createContext("/api/lyric", Server::handleLyric);
        server.createContext("/api/bg", Server::handleBg);
        server.createContext("/", Server::handleIndex);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("[muonium] standalone ready: http://127.0.0.1:" + PORT);
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null || q.isEmpty()) return m;
        for (String pair : q.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                String k = URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
                m.put(k, v);
            }
        }
        return m;
    }

    private static void cors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
    }

    private static void sendJson(HttpExchange ex, int code, Object obj) throws IOException {
        cors(ex);
        byte[] body = GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private static void handleSearch(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        String kw = q.getOrDefault("kw", "").trim();
        if (kw.isEmpty()) { sendJson(ex, 200, List.of()); return; }
        try {
            List<Music> r = CloudMusic.search(kw);
            synchronized (lock) { lastResults = r; }
            JsonArray arr = new JsonArray();
            for (Music m : r) {
                JsonObject o = new JsonObject();
                o.addProperty("id", m.getId());
                o.addProperty("name", m.getName());
                o.addProperty("artist", m.getArtistsName());
                o.addProperty("duration", m.getDuration());
                o.addProperty("source", m.getSource() == null ? "" : m.getSource().name());
                arr.add(o);
            }
            sendJson(ex, 200, arr);
        } catch (Throwable t) {
            sendJson(ex, 500, Map.of("error", String.valueOf(t)));
        }
    }

    private static void handleHot(HttpExchange ex) throws IOException {
        try {
            RequestAnswer ans = CloudMusicApi.searchHotDetail();
            JsonObject jo = ans == null ? null : ans.toJsonObject();
            JsonArray data = jo == null ? null : jo.getAsJsonArray("data");
            List<String> words = new ArrayList<>();
            if (data != null) for (JsonElement e : data) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("searchWord")) words.add(o.get("searchWord").getAsString());
            }
            sendJson(ex, 200, words);
        } catch (Throwable t) {
            sendJson(ex, 500, Map.of("error", String.valueOf(t)));
        }
    }

    private static Music buildNeteaseMusic(JsonObject s) {
        String id = String.valueOf(s.get("id").getAsLong());
        String name = s.get("name").getAsString();
        StringBuilder ar = new StringBuilder();
        JsonArray arrs = s.getAsJsonArray("ar");
        if (arrs != null) for (int k = 0; k < arrs.size(); k++) {
            if (k > 0) ar.append("/");
            ar.append(arrs.get(k).getAsJsonObject().get("name").getAsString());
        }
        String album = "", cover = "";
        if (s.has("al") && !s.get("al").isJsonNull()) {
            JsonObject al = s.getAsJsonObject("al");
            if (al.has("name")) album = al.get("name").getAsString();
            if (al.has("picUrl")) cover = al.get("picUrl").getAsString();
        }
        long dt = s.has("dt") ? s.get("dt").getAsLong() : 0;
        int fee = s.has("fee") ? s.get("fee").getAsInt() : 0;
        Track t = new Track(MusicSource.NETEASE, id, "", name, ar.toString(), album, dt, cover, fee > 0);
        return Music.fromCadenceTrack(t);
    }

    private static JsonObject musicJson(Music m) {
        JsonObject o = new JsonObject();
        o.addProperty("id", m.getId());
        o.addProperty("name", m.getName());
        o.addProperty("artist", m.getArtistsName());
        o.addProperty("duration", m.getDuration());
        o.addProperty("source", m.getSource() == null ? "" : m.getSource().name());
        return o;
    }

    private static void handleCharts(HttpExchange ex) throws IOException {
        try {
            RequestAnswer ans = CloudMusicApi.topListDetail();
            JsonObject jo = ans == null ? null : ans.toJsonObject();
            JsonArray list = jo == null ? null : jo.getAsJsonArray("list");
            JsonArray out = new JsonArray();
            if (list != null) for (JsonElement e : list) {
                JsonObject o = e.getAsJsonObject();
                JsonObject c = new JsonObject();
                c.addProperty("id", o.get("id").getAsLong());
                c.addProperty("name", o.get("name").getAsString());
                c.addProperty("cover", o.has("coverImgUrl") ? o.get("coverImgUrl").getAsString() : "");
                c.addProperty("count", o.has("trackCount") ? o.get("trackCount").getAsInt() : 0);
                out.add(c);
            }
            sendJson(ex, 200, out);
        } catch (Throwable t) {
            sendJson(ex, 500, Map.of("error", String.valueOf(t)));
        }
    }

    private static void handleChartTracks(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        long pid = Long.parseLong(q.getOrDefault("pid", "-1"));
        try {
            RequestAnswer ans = CloudMusicApi.playlistTrackAll(pid, 0);
            JsonObject jo = ans == null ? null : ans.toJsonObject();
            JsonArray songs = jo == null ? null : jo.getAsJsonArray("songs");
            List<Music> list = new ArrayList<>();
            JsonArray arr = new JsonArray();
            if (songs != null) for (JsonElement e : songs) {
                Music m = buildNeteaseMusic(e.getAsJsonObject());
                list.add(m);
                arr.add(musicJson(m));
            }
            synchronized (lock) { lastResults = list; }
            sendJson(ex, 200, arr);
        } catch (Throwable t) {
            sendJson(ex, 500, Map.of("error", String.valueOf(t)));
        }
    }

    private static void handleLyric(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        long id;
        try { id = Long.parseLong(q.getOrDefault("id", "-1")); } catch (Exception e) { sendJson(ex, 400, Map.of("error", "bad id")); return; }
        try {
            RequestAnswer ans = CloudMusicApi.lyricNew(id);
            JsonObject jo = ans == null ? null : ans.toJsonObject();
            String lrc = "", tly = "";
            if (jo != null) {
                JsonObject l = jo.getAsJsonObject("lrc");
                if (l != null && l.has("lyric")) lrc = l.get("lyric").getAsString();
                JsonObject t = jo.getAsJsonObject("tlyric");
                if (t != null && t.has("lyric")) tly = t.get("lyric").getAsString();
            }
            sendJson(ex, 200, Map.of("lyric", lrc, "tlyric", tly));
        } catch (Throwable t) {
            sendJson(ex, 500, Map.of("error", String.valueOf(t)));
        }
    }

    private static Music findById(long id) {
        synchronized (lock) {
            for (Music m : lastResults) if (m.getId() == id) return m;
        }
        return null;
    }

    private static final long CACHE_LIMIT = 300L * 1024 * 1024;
    private static Path cacheDir() { return dataDir.resolve("cache"); }

    private static String contentTypeOf(String fmt) {
        return switch (fmt.toLowerCase()) {
            case "flac" -> "audio/flac";
            case "aac", "m4a", "mp4" -> "audio/mp4";
            case "ogg", "oga" -> "audio/ogg";
            default -> "audio/mpeg";
        };
    }

    private static void enforceCacheLimit() throws IOException {
        Path dir = cacheDir();
        if (!Files.exists(dir)) return;
        List<Path> files = new ArrayList<>();
        long total = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path p : ds) {
                if (Files.isRegularFile(p) && !p.toString().endsWith(".part")) {
                    files.add(p); total += Files.size(p);
                }
            }
        }
        files.sort(Comparator.comparingLong(p -> p.toFile().lastModified()));
        Iterator<Path> it = files.iterator();
        while (total > CACHE_LIMIT && it.hasNext()) {
            Path p = it.next();
            total -= Files.size(p);
            Files.deleteIfExists(p);
        }
    }

    private static void sendCachedFile(HttpExchange ex, Path file, String contentType, String range) throws IOException {
        long size = Files.size(file);
        cors(ex);
        ex.getResponseHeaders().set("Content-Type", contentType);
        if (range == null || !range.startsWith("bytes=")) {
            ex.sendResponseHeaders(200, size);
            try (OutputStream os = ex.getResponseBody();
                 FileChannel fc = FileChannel.open(file, StandardOpenOption.READ)) {
                fc.transferTo(0, size, Channels.newChannel(os));
            }
            return;
        }
        String[] se = range.substring(6).split("-");
        long start = Long.parseLong(se[0]);
        long end = se.length > 1 && !se[1].isEmpty() ? Long.parseLong(se[1]) : size - 1;
        if (end >= size) end = size - 1;
        if (start < 0) start = 0;
        long len = end - start + 1;
        ex.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + size);
        ex.sendResponseHeaders(206, len);
        try (OutputStream os = ex.getResponseBody();
             FileChannel fc = FileChannel.open(file, StandardOpenOption.READ)) {
            fc.transferTo(start, len, Channels.newChannel(os));
        }
    }

    private static void handleStream(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        long id = Long.parseLong(q.getOrDefault("id", "-1"));
        Music m = findById(id);
        if (m == null) { sendJson(ex, 404, Map.of("error", "track not found")); return; }
        String qs = q.getOrDefault("quality", "");
        if (!qs.isEmpty()) {
            try { CloudMusic.quality = Quality.valueOf(qs); } catch (Exception ignore) {}
        }
        try {
            Tuple<String, String> u = m.getPlayUrl();
            if (u == null || u.getA() == null) { sendJson(ex, 404, Map.of("error", "no url")); return; }
            String fmt = u.getB() == null ? "mp3" : u.getB();
            String contentType = contentTypeOf(fmt);
            String range = ex.getRequestHeaders().getFirst("Range");

            Path cacheFile = cacheDir().resolve(id + "_" + (qs.isEmpty() ? "HIGHER" : qs) + "." + fmt);
            if (Files.exists(cacheFile) && Files.size(cacheFile) > 0) {
                sendCachedFile(ex, cacheFile, contentType, range);
                return;
            }

            HttpURLConnection conn = (HttpURLConnection) new URL(u.getA()).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            Map<String, String> h = CloudMusic.getHeaders();
            if (h != null) for (Map.Entry<String, String> e : h.entrySet())
                conn.setRequestProperty(e.getKey(), e.getValue());
            if (range != null) conn.setRequestProperty("Range", range);
            conn.connect();

            int code = conn.getResponseCode();
            cors(ex);
            ex.getResponseHeaders().set("Content-Type", contentType);
            String cl = conn.getHeaderField("Content-Length");
            String cr = conn.getHeaderField("Content-Range");
            if (cr != null) ex.getResponseHeaders().set("Content-Range", cr);
            long len = cl == null ? 0 : Long.parseLong(cl);
            ex.sendResponseHeaders(code, len == 0 ? -1 : len);

            boolean cacheable = range == null && code == 200;
            Path part = cacheFile.resolveSibling(cacheFile.getFileName() + ".part");
            try (InputStream in = conn.getInputStream(); OutputStream os = ex.getResponseBody()) {
                if (cacheable) {
                    Files.createDirectories(cacheDir());
                    enforceCacheLimit();
                    try (OutputStream cache = Files.newOutputStream(part)) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) { os.write(buf, 0, n); cache.write(buf, 0, n); }
                    }
                    Files.move(part, cacheFile, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    in.transferTo(os);
                    Files.deleteIfExists(part);
                }
            } catch (Throwable t) {
                Files.deleteIfExists(part);
                throw t;
            }
        } catch (Throwable t) {
            if (!ex.getResponseHeaders().containsKey("Content-Type"))
                sendJson(ex, 500, Map.of("error", String.valueOf(t)));
        }
    }

    private static void handleIndex(HttpExchange ex) throws IOException {
        Path file = webRoot.resolve("index.html");
        if (!Files.exists(file)) {
            ex.sendResponseHeaders(404, -1); return;
        }
        byte[] body = Files.readAllBytes(file);
        cors(ex);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }

    private static Path bgImage() { return dataDir.resolve("bg.dat"); }
    private static Path bgMime()  { return dataDir.resolve("bg.mime"); }

    private static void handleBg(HttpExchange ex) throws IOException {
        cors(ex);
        if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
            String mime = ex.getRequestHeaders().getFirst("Content-Type");
            if (mime == null || mime.isEmpty()) mime = "image/png";
            try (InputStream in = ex.getRequestBody();
                 OutputStream out = Files.newOutputStream(bgImage())) {
                in.transferTo(out);
            }
            Files.writeString(bgMime(), mime, StandardCharsets.UTF_8);
            sendJson(ex, 200, Map.of("ok", true));
            return;
        }
        if (!Files.exists(bgImage())) { ex.sendResponseHeaders(204, -1); return; }
        byte[] body = Files.readAllBytes(bgImage());
        String mime = Files.exists(bgMime())
                ? Files.readString(bgMime(), StandardCharsets.UTF_8).trim() : "image/png";
        ex.getResponseHeaders().set("Content-Type", mime);
        ex.sendResponseHeaders(200, body.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(body); }
    }
}
