package com.course_aura_hub.video.service;

import com.course_aura_hub.file.storage.ObjectStore;
import com.course_aura_hub.video.config.VideoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Turns one stored video into an HLS playlist and segments with FFmpeg, in a temporary folder, and stores them
 * next to each other under private/hls. Runs outside any transaction; it can take minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VideoTranscoder {

    static final String PLAYLIST = "index.m3u8";

    private final ObjectStore objectStore;
    private final VideoProperties properties;

    /** @param folder storage folder for the output, such as private/hls/12/34 */
    public Result transcode(String sourceKey, String folder) throws IOException, InterruptedException {
        Path work = Files.createTempDirectory("transcode-");
        try {
            Path input = work.resolve("source" + extension(sourceKey));
            objectStore.download(sourceKey, input);

            Path output = Files.createDirectory(work.resolve("hls"));
            int seconds = (int) Math.ceil(probeDuration(input, work));
            run(List.of(properties.ffmpegPath(), "-hide_banner", "-loglevel", "error", "-y", "-i", input.toString(),
                    "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "128k",
                    "-f", "hls", "-hls_time", String.valueOf(properties.segmentSeconds()),
                    "-hls_playlist_type", "vod",
                    "-hls_segment_filename", output.resolve("segment_%05d.ts").toString(),
                    output.resolve(PLAYLIST).toString()), work);

            try (Stream<Path> files = Files.list(output)) {
                for (Path file : files.sorted().toList()) {
                    String name = file.getFileName().toString();
                    objectStore.upload(folder + "/" + name, file,
                            name.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t");
                }
            }
            return new Result(folder + "/" + PLAYLIST, seconds);
        } finally {
            deleteQuietly(work);
        }
    }

    public record Result(String playlistKey, int durationSeconds) {
    }

    private double probeDuration(Path input, Path work) throws IOException, InterruptedException {
        String out = run(List.of(properties.ffprobePath(), "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", input.toString()), work).trim();
        try {
            return Double.parseDouble(out);
        } catch (NumberFormatException ex) {
            throw new IOException("ffprobe could not read the video's duration");
        }
    }

    /** Runs a tool with its output in a file, so a chatty process can never block on a full pipe. */
    private String run(List<String> command, Path work) throws IOException, InterruptedException {
        Path log = Files.createTempFile(work, "tool-", ".log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IOException(command.getFirst() + " took longer than " + properties.timeout());
        }
        String output = Files.readString(log, StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            throw new IOException(command.getFirst() + " failed: " + output.substring(0, Math.min(output.length(), 500)));
        }
        return output;
    }

    private static String extension(String key) {
        int dot = key.lastIndexOf('.');
        return dot < 0 ? "" : key.substring(dot);
    }

    private static void deleteQuietly(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ex) {
            log.warn("Could not clean up transcoding folder {}", root);
        }
    }
}
