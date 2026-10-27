package de.uniwue.zpd.dachs.larex.backend.service.image;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import de.uniwue.zpd.dachs.larex.backend.config.ImagePresentationProperties;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Disposable, revisioned display cache. Source files are never rewritten. */
@Service
public class ImagePresentationService {
    public enum Size {
        thumbnail(320), overview(1600), detail(3200), original(0);
        final int maxDimension;
        Size(int maxDimension) { this.maxDimension = maxDimension; }
    }
    public record Presentation(byte[] bytes, int originalWidth, int originalHeight, String mediaType) {
        public Presentation(byte[] bytes, int width, int height) { this(bytes, width, height, "image/jpeg"); }
    }
    private record Job(Future<Presentation> result) {}
    private final UploadPathService paths;
    private final ImagePresentationProperties limits;
    private final ImageDecoderProcess decoder;
    private final Path directory;
    private final ThreadPoolExecutor executor;
    private final Map<String, Job> pending = new HashMap<>();
    private final Cache<String, Presentation> cache = Caffeine.newBuilder().maximumWeight(64L * 1024 * 1024)
            .weigher((String key, Presentation value) -> value.bytes().length).expireAfterAccess(Duration.ofMinutes(30)).build();

    public ImagePresentationService(UploadPathService paths) {
        this(paths, ImagePresentationProperties.defaults(), new ImageDecoderProcess());
    }
    @Autowired
    public ImagePresentationService(UploadPathService paths, ImagePresentationProperties limits, ImageDecoderProcess decoder) {
        this.paths = paths; this.limits = limits; this.decoder = decoder;
        directory = limits.cacheDirectory() == null ? paths.root().resolve(".presentations") : limits.cacheDirectory().toAbsolutePath();
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(limits.queueCapacity()), runnable -> {
                    Thread thread = new Thread(runnable, "image-presentation"); thread.setDaemon(true); return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }
    public NativeImagePresentation nativeImage(String sourcePath) throws IOException {
        return NativeImagePresentation.open(paths.resolve(sourcePath));
    }

    public Presentation get(String sourcePath, Size size) throws IOException {
        Path source = paths.resolve(sourcePath);
        BasicFileAttributes attributes = Files.readAttributes(source, BasicFileAttributes.class);
        if (size != Size.original && attributes.size() > limits.maxSourceBytes()) throw new ImagePresentationException(413, "Image exceeds the configured source file limit");
        String key = key(source, attributes, size);
        var ready = cache.getIfPresent(key);
        if (ready != null) return ready;
        // A warm disk hit must not wait behind an unrelated cold conversion.
        var disk = readDisk(key);
        if (disk != null) { cache.put(key, disk); cache.cleanUp(); return disk; }
        Job job;
        synchronized (pending) {
            job = pending.get(key);
            if (job == null) {
                pending.entrySet().removeIf(entry -> entry.getValue().result().isDone());
                try {
                    job = new Job(executor.submit(() -> {
                        var cached = readDisk(key);
                        if (cached != null) { cache.put(key, cached); cache.cleanUp(); return cached; }
                        Files.createDirectories(directory);
                        Path scratch = Files.createTempDirectory(directory, "work-");
                        try {
                            var result = decoder.decode(source, scratch, size.maxDimension, limits);
                            // Do not publish data if a source was replaced during conversion.
                            if (!key.equals(key(source, Files.readAttributes(source, BasicFileAttributes.class), size)))
                                throw new ImagePresentationException(409, "Image changed during conversion; please retry");
                            writeDisk(key, result, source, attributes);
                            cache.put(key, result);
                            cache.cleanUp();
                            return result;
                        } finally { deleteTree(scratch); }
                    }));
                    pending.put(key, job);
                } catch (RejectedExecutionException e) {
                    throw new ImagePresentationException(503, "Image conversion is busy. Please retry shortly.");
                }
            }
        }
        try {
            return job.result().get(limits.requestTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            job.result().cancel(true);
            executor.remove((Runnable) job.result());
            throw new ImagePresentationException(503, "Image conversion queue timed out. Please retry shortly.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new ImagePresentationException(503, "Image request interrupted");
        } catch (CancellationException e) {
            throw new ImagePresentationException(503, "Image conversion cancelled; please retry");
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) throw io;
            throw new ImagePresentationException(422, "Unable to create a display image");
        } finally {
            synchronized (pending) { if (job.result().isDone()) pending.remove(key, job); }
        }
    }

    private String key(Path source, BasicFileAttributes attributes, Size size) throws IOException {
        String value = "stored-pixels-v4:" + limits.maxSourcePixels() + ":" + limits.maxSourceDimension() + ":" + source + ":" + attributes.size() + ":" + attributes.lastModifiedTime() + ":" + size;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    private synchronized Presentation readDisk(String key) {
        Path meta = directory.resolve(key + ".meta"), data = directory.resolve(key + ".data");
        if (!Files.isRegularFile(meta) || !Files.isRegularFile(data) || Files.isSymbolicLink(meta) || Files.isSymbolicLink(data)) return null;
        try {
            if (Files.size(data) > Math.min(limits.maxDiskCacheBytes(), 67_108_864L)) return null;
            Properties p = new Properties(); try (var input = Files.newInputStream(meta)) { p.load(input); }
            byte[] bytes = Files.readAllBytes(data);
            int width = Integer.parseInt(p.getProperty("width")), height = Integer.parseInt(p.getProperty("height"));
            String type = p.getProperty("type");
            if (width <= 0 || height <= 0 || !("image/jpeg".equals(type) || "image/png".equals(type))
                    || !digest(bytes).equals(p.getProperty("digest"))) throw new IllegalArgumentException("Corrupt derivative cache entry");
            Files.setLastModifiedTime(meta, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));
            return new Presentation(bytes, width, height, type);
        } catch (IOException | IllegalArgumentException | NullPointerException e) {
            try { Files.deleteIfExists(meta); Files.deleteIfExists(data); } catch (IOException ignored) {}
            return null;
        }
    }
    private synchronized void writeDisk(String key, Presentation value, Path source, BasicFileAttributes attributes) {
        // A cache write failure must not prevent displaying an already converted image.
        Path data = directory.resolve(key + ".data"), meta = directory.resolve(key + ".meta");
        try {
            if (value.bytes().length > limits.maxDiskCacheBytes()) return;
            var properties = new Properties();
            properties.setProperty("width", String.valueOf(value.originalWidth())); properties.setProperty("height", String.valueOf(value.originalHeight()));
            properties.setProperty("type", value.mediaType()); properties.setProperty("source", source.toString());
            properties.setProperty("digest", digest(value.bytes()));
            properties.setProperty("size", String.valueOf(attributes.size())); properties.setProperty("modified", attributes.lastModifiedTime().toString());
            Path temporary = Files.createTempFile(directory, "cache-", ".tmp");
            try {
                Files.write(temporary, value.bytes()); move(temporary, data);
                try (var output = Files.newOutputStream(temporary)) { properties.store(output, null); }
                move(temporary, meta); // Metadata is the commit marker.
            } finally { Files.deleteIfExists(temporary); }
            pruneDisk();
        } catch (IOException ignored) {
            try { Files.deleteIfExists(meta); Files.deleteIfExists(data); } catch (IOException ignoredCleanup) {}
        }
    }
    private String digest(byte[] bytes) throws IOException {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    private void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    @Scheduled(fixedDelay = 60000)
    public synchronized void pruneDisk() {
        if (!Files.isDirectory(directory)) return;
        try (var stream = Files.list(directory)) {
            var entries = stream.filter(p -> p.getFileName().toString().endsWith(".meta")).sorted(Comparator.comparingLong(this::modified)).toList();
            long total = 0;
            var valid = new ArrayList<Path>();
            long cutoff = System.currentTimeMillis() - limits.diskRetention().toMillis();
            for (Path meta : entries) {
                Path data = directory.resolve(meta.getFileName().toString().replace(".meta", ".data"));
                try {
                    var p = new Properties();
                    try (var input = Files.newInputStream(meta)) { p.load(input); }
                    Path source = Path.of(p.getProperty("source", ""));
                    if (modified(meta) < cutoff || !Files.isRegularFile(source) || !Files.isRegularFile(data)
                            || !p.getProperty("size", "").equals(String.valueOf(Files.size(source)))
                            || !p.getProperty("modified", "").equals(Files.getLastModifiedTime(source).toString())) {
                        Files.deleteIfExists(meta); Files.deleteIfExists(data);
                    } else { total += Files.size(data); valid.add(meta); }
                } catch (IOException | IllegalArgumentException e) {
                    try { Files.deleteIfExists(meta); Files.deleteIfExists(data); } catch (IOException ignored) {}
                }
            }
            for (Path meta : valid) {
                if (total <= limits.maxDiskCacheBytes()) break;
                Path data = directory.resolve(meta.getFileName().toString().replace(".meta", ".data"));
                total -= Files.size(data); Files.deleteIfExists(meta); Files.deleteIfExists(data);
            }
            try (var leftovers = Files.list(directory)) {
                for (Path path : leftovers.toList()) {
                    String name = path.getFileName().toString();
                    if (name.endsWith(".data") && !Files.exists(directory.resolve(name.replace(".data", ".meta")))) Files.deleteIfExists(path);
                    else if ((name.startsWith("work-") || name.startsWith("cache-")) && modified(path) < System.currentTimeMillis() - 3_600_000) deleteTree(path);
                }
            }
        } catch (IOException | IllegalArgumentException ignored) { /* retry on the next maintenance pass */ }
    }
    private long modified(Path path) { try { return Files.getLastModifiedTime(path).toMillis(); } catch (IOException e) { return 0; } }
    private static void deleteTree(Path root) {
        try (var entries = Files.walk(root)) {
            for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        } catch (IOException ignored) {}
    }
    @PreDestroy
    public void close() { executor.shutdownNow(); cache.invalidateAll(); }
}
