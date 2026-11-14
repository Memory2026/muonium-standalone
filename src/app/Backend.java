package app;

import com.muoniumplayer.core.ncm.music.CloudMusic;
import com.muoniumplayer.core.ncm.music.AudioPlayer;
import com.muoniumplayer.core.ncm.music.dto.Music;
import com.muoniumplayer.core.utils.Tuple;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * 对剥离出的后端做薄封装：搜索 -> 取流 -> 下载到临时文件 -> AudioPlayer 出声。
 * 不依赖任何 MC 界面类。
 */
public class Backend {

    public interface Listener {
        void onPlaying(Music m);
        void onPlaybackFinished();
        void onError(String msg);
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();

    private AudioPlayer player;
    private File currentFile;
    private List<Music> queue = new ArrayList<>();
    private int index = -1;
    private Music current;
    private float volume = 1f;
    private Listener listener;

    public void setListener(Listener l) { this.listener = l; }

    public void init() { CloudMusic.initNCM(); }

    public List<Music> search(String kw) { return CloudMusic.search(kw); }

    public void playQueue(List<Music> list, int idx) {
        this.queue = list;
        this.index = idx;
        loadAndPlay();
    }

    private void loadAndPlay() {
        if (index < 0 || index >= queue.size()) return;
        Music m = queue.get(index);
        io.submit(() -> {
            try {
                stopPlayer();
                Tuple<String, String> u = m.getPlayUrl();
                if (u == null || u.getA() == null) {
                    if (listener != null) listener.onError("无可用播放链接：" + m.getName());
                    return;
                }
                String fmt = u.getB() == null ? "mp3" : u.getB();
                Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "muonium-standalone");
                Files.createDirectories(dir);
                File f = dir.resolve("track-" + m.getId() + "." + fmt).toFile();
                if (!f.exists() || f.length() == 0) {
                    URLConnection conn = new URL(u.getA()).openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
                    Map<String, String> h = CloudMusic.getHeaders();
                    if (h != null) for (Map.Entry<String, String> e : h.entrySet())
                        conn.setRequestProperty(e.getKey(), e.getValue());
                    try (InputStream in = conn.getInputStream();
                         OutputStream out = Files.newOutputStream(f.toPath())) {
                        in.transferTo(out);
                    }
                }
                synchronized (lock) {
                    current = m;
                    currentFile = f;
                    player = new AudioPlayer(f);
                    player.setVolume(volume);
                    player.setAfterPlayed(() -> { if (listener != null) listener.onPlaybackFinished(); });
                    player.play();
                }
                if (listener != null) listener.onPlaying(m);
            } catch (Throwable t) {
                if (listener != null) listener.onError(t.toString());
            }
        });
    }

    private void stopPlayer() {
        synchronized (lock) {
            if (player != null) {
                try { player.close(); } catch (Throwable ignored) {}
                player = null;
            }
        }
    }

    public void pause()  { if (player != null) player.pause(); }
    public void resume() { if (player != null) player.unpause(); }
    public void setVolume(float v) { volume = v; if (player != null) player.setVolume(v); }

    public boolean isPlaying()  { return player != null && player.isPlaying(); }
    public boolean isPausing()   { return player != null && player.isPausing(); }
    public boolean isFinished()  { return player == null || player.isFinished(); }
    public long currentSec()     { return player == null ? 0 : (long) player.getCurrentTimeSeconds(); }
    public long totalSec()       { return player == null ? 0 : (long) player.getTotalTimeSeconds(); }
    public Music current()       { return current; }
    public int index()           { return index; }

    public void next() { if (index + 1 < queue.size()) { index++; loadAndPlay(); } }
    public void prev() { if (index - 1 >= 0) { index--; loadAndPlay(); } }

    public void dispose() { stopPlayer(); io.shutdownNow(); }
}
