package de.uniwue.zpd.dachs.larex.backend.service.image;

import de.uniwue.zpd.dachs.larex.backend.config.ImagePresentationProperties;
import org.springframework.stereotype.Component;
import java.io.File;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

@Component
public class ImageDecoderProcess {
    public ImagePresentationService.Presentation decode(Path source, Path scratch, int edge, ImagePresentationProperties limits) throws IOException {
        Path data = scratch.resolve("data"), metadata = scratch.resolve("metadata");
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xmx" + limits.workerHeapMib() + "m"); command.add("-Djava.awt.headless=true");
        String classpath = classpath();
        String archive = executableArchive(classpath);
        if (archive != null) {
            command.add("-Dloader.main=" + ImagePresentationWorker.class.getName());
            command.add("-cp"); command.add(archive); command.add("org.springframework.boot.loader.launch.PropertiesLauncher");
        } else {
            command.add("-cp"); command.add(classpath); command.add(ImagePresentationWorker.class.getName());
        }
        command.addAll(List.of(source.toString(), data.toString(), metadata.toString(), String.valueOf(edge),
                String.valueOf(limits.maxSourcePixels()), String.valueOf(limits.maxSourceDimension())));
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().remove("JAVA_TOOL_OPTIONS"); builder.environment().remove("JDK_JAVA_OPTIONS");
        Process process = builder.start();
        try {
            if (!process.waitFor(limits.decodeTimeout().toMillis(), TimeUnit.MILLISECONDS))
                throw new ImagePresentationException(504, "Image conversion timed out. Please retry or download the original.");
            if (!Files.isRegularFile(metadata)) throw new ImagePresentationException(422, "Image conversion exceeded its resource limits");
            var properties = new Properties(); try (var input = Files.newInputStream(metadata)) { properties.load(input); }
            if (properties.containsKey("status"))
                throw new ImagePresentationException(Integer.parseInt(properties.getProperty("status")), properties.getProperty("message"));
            if (edge != 0 && Files.size(data) > 67_108_864L)
                throw new ImagePresentationException(413, "Display image exceeds the encoded response limit");
            return new ImagePresentationService.Presentation(Files.readAllBytes(data),
                    Integer.parseInt(properties.getProperty("width")), Integer.parseInt(properties.getProperty("height")), properties.getProperty("type"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new ImagePresentationException(503, "Image conversion cancelled; please retry");
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                try { process.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
    }
    private String classpath() {
        var paths = new LinkedHashSet<>(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
        for (ClassLoader loader = getClass().getClassLoader(); loader != null; loader = loader.getParent()) {
            if (loader instanceof URLClassLoader urls) for (var url : urls.getURLs()) {
                if ("file".equals(url.getProtocol())) try { paths.add(Path.of(url.toURI()).toString()); } catch (Exception ignored) {}
            }
        }
        return String.join(File.pathSeparator, paths);
    }
    private String executableArchive(String classpath) {
        for (String path : classpath.split(File.pathSeparator)) {
            if (!path.endsWith(".jar")) continue;
            try (var jar = new JarFile(path)) {
                if (jar.getJarEntry("BOOT-INF/classes/" + ImagePresentationWorker.class.getName().replace('.', '/') + ".class") != null) return path;
            } catch (IOException ignored) {}
        }
        return null;
    }
}
