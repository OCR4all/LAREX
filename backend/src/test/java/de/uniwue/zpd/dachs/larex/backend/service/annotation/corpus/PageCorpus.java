package de.uniwue.zpd.dachs.larex.backend.service.annotation.corpus;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlConversionService;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlPrettyPrinter;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class PageCorpus {

    static final Path DOCUMENTS = Path.of("src/test/resources/page-corpus");
    static final Path GOLDENS = Path.of("src/test/resources/page-corpus-goldens");
    static final Path KNOWN_DIFFERENCES = DOCUMENTS.resolve("known-differences.txt");
    static final boolean UPDATE = "true".equalsIgnoreCase(System.getenv("LAREX_UPDATE_GOLDENS"));

    private static final String KNOWN_DIFFERENCES_HEADER = """
        # Differences the PAGE XML pipeline produces for the documents in this folder (see PageCorpusTest).
        # Format: <document> | <stage>: <difference>
        #
        # ingest: the stored file after upload canonicalisation, compared with the uploaded document
        #         (element namespaces are ignored when the version changes).
        # save:   the file the editor writes after loading the stored file without edits, compared with
        #         the stored file.
        # FAILED <stage>: the stage threw; the message is in page-corpus-goldens/<document>/failure.txt.
        #
        # This is a ratchet: an unlisted difference fails the test, and so does a listed one that no longer
        # occurs. LAREX_UPDATE_GOLDENS=true rewrites this file; review every changed line.
        """;

    private static final Pattern TIMESTAMP = Pattern.compile(
        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})?");
    private static final Pattern TEMP_FILE = Pattern.compile("page\\.xml\\d+\\.tmp");
    private static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(2);

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private final PageXmlConversionService conversionService = new PageXmlConversionService();
    private final PageXmlToAnnotationParser parser = new PageXmlToAnnotationParser(new Page4jToDtoMapper());
    private final AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());

    record Result(
        String source,
        String sourceVersion,
        String canonical,
        List<String> ingestMessages,
        PageDto dto,
        String export,
        String failure,
        Instant startedAt,
        Instant finishedAt
    ) {
        boolean versionChanged() {
            return sourceVersion != null && !PageXmlConversionService.PRIMARY_PAGE_VERSION.equals(sourceVersion);
        }
    }

    static List<String> documents() {
        try (Stream<Path> files = Files.walk(DOCUMENTS)) {
            return files
                .filter(path -> path.toString().endsWith(".xml"))
                .map(path -> DOCUMENTS.relativize(path).toString().replace('\\', '/'))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    Result run(String document, Path workDirectory) throws IOException {
        Instant startedAt = Instant.now();
        Result result = runPipeline(document, workDirectory);
        Instant finishedAt = Instant.now();
        return new Result(
            result.source(),
            result.sourceVersion(),
            maskRunTimestamps(result.canonical(), startedAt, finishedAt),
            result.ingestMessages(),
            result.dto(),
            maskRunTimestamps(result.export(), startedAt, finishedAt),
            result.failure() == null ? null : TEMP_FILE.matcher(result.failure()).replaceAll("page.xml<random>.tmp"),
            startedAt,
            finishedAt
        );
    }

    private Result runPipeline(String document, Path workDirectory) throws IOException {
        String source = Files.readString(DOCUMENTS.resolve(document), StandardCharsets.UTF_8);
        Path file = workDirectory.resolve("page.xml");
        Files.writeString(file, source, StandardCharsets.UTF_8);

        String stage = "ingest";
        String sourceVersion = null;
        String canonical = null;
        List<String> ingestMessages = List.of();
        PageDto dto = null;
        String export = null;
        try {
            sourceVersion = conversionService.detectPageVersion(file);
            if (!PageXmlConversionService.PRIMARY_PAGE_VERSION.equals(sourceVersion)) {
                ingestMessages = conversionService
                    .convertFileInPlace(file, PageXmlConversionService.PRIMARY_PAGE_VERSION)
                    .messages();
            }
            PageXmlPrettyPrinter.prettyPrint(file);
            canonical = Files.readString(file, StandardCharsets.UTF_8);

            stage = "load";
            dto = parser.parse(file, null);

            stage = "save";
            export = exporter.export(dto, null);
            return new Result(source, sourceVersion, canonical, ingestMessages, dto, export, null, null, null);
        } catch (Exception e) {
            String failure = stage + ": " + describe(e).replace(workDirectory.toString(), "<work>");
            return new Result(source, sourceVersion, canonical, ingestMessages, dto, export, failure, null, null);
        }
    }

    static String failedStage(Result result) {
        return result.failure() == null ? null : result.failure().substring(0, result.failure().indexOf(':'));
    }

    static SortedMap<String, String> goldenFiles(Result result) {
        SortedMap<String, String> files = new TreeMap<>();
        if (result.versionChanged() && result.canonical() != null) {
            files.put("canonical.xml", result.canonical());
        }
        if (!result.ingestMessages().isEmpty()) {
            files.put("ingest-messages.txt", String.join("\n", result.ingestMessages()) + "\n");
        }
        if (result.dto() != null) {
            String json = JSON.writeValueAsString(result.dto()) + "\n";
            files.put("dto.json", maskRunTimestamps(json, result.startedAt(), result.finishedAt()));
        }
        if (result.export() != null) {
            files.put("export.xml", result.export());
        }
        if (result.failure() != null) {
            files.put("failure.txt", result.failure() + "\n");
        }
        return files;
    }

    static SortedMap<String, String> storedGoldenFiles(String document) throws IOException {
        Path directory = goldenDirectory(document);
        SortedMap<String, String> files = new TreeMap<>();
        if (!Files.isDirectory(directory)) {
            return files;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                files.put(entry.getFileName().toString(), Files.readString(entry, StandardCharsets.UTF_8));
            }
        }
        return files;
    }

    static void storeGoldenFiles(String document, Map<String, String> files) throws IOException {
        Path directory = goldenDirectory(document);
        if (Files.isDirectory(directory)) {
            try (Stream<Path> entries = Files.list(directory)) {
                for (Path entry : entries.toList()) {
                    Files.delete(entry);
                }
            }
        }
        Files.createDirectories(directory);
        for (var entry : files.entrySet()) {
            Files.writeString(directory.resolve(entry.getKey()), entry.getValue(), StandardCharsets.UTF_8);
        }
    }

    static List<String> differences(Result result) throws Exception {
        List<String> differences = new ArrayList<>();
        if (result.failure() != null) {
            differences.add("FAILED " + failedStage(result));
        }
        if (result.canonical() != null) {
            List<XmlDiff.Difference> ingest = result.versionChanged()
                ? XmlDiff.compareIgnoringElementNamespaces(result.source(), result.canonical())
                : XmlDiff.compare(result.source(), result.canonical());
            ingest.forEach(difference -> differences.add("ingest: " + difference));
        }
        if (result.canonical() != null && result.export() != null) {
            XmlDiff.compare(result.canonical(), result.export())
                .forEach(difference -> differences.add("save: " + difference));
        }
        return differences;
    }

    static SortedMap<String, List<String>> knownDifferences() throws IOException {
        SortedMap<String, List<String>> known = new TreeMap<>();
        if (!Files.exists(KNOWN_DIFFERENCES)) {
            return known;
        }
        for (String line : Files.readAllLines(KNOWN_DIFFERENCES, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf(" | ");
            if (separator < 0) {
                throw new IllegalStateException("Malformed line in " + KNOWN_DIFFERENCES + ": " + line);
            }
            known.computeIfAbsent(line.substring(0, separator), ignored -> new ArrayList<>())
                .add(line.substring(separator + 3));
        }
        return known;
    }

    static void storeKnownDifferences(SortedMap<String, List<String>> differences) throws IOException {
        StringBuilder content = new StringBuilder(KNOWN_DIFFERENCES_HEADER);
        for (var entry : differences.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            content.append('\n');
            for (String difference : entry.getValue()) {
                content.append(entry.getKey()).append(" | ").append(difference).append('\n');
            }
        }
        Files.writeString(KNOWN_DIFFERENCES, content.toString(), StandardCharsets.UTF_8);
    }

    static String maskRunTimestamps(String text, Instant startedAt, Instant finishedAt) {
        if (text == null) {
            return null;
        }
        Matcher matcher = TIMESTAMP.matcher(text);
        StringBuilder masked = new StringBuilder();
        while (matcher.find()) {
            Instant instant = toInstant(matcher.group());
            boolean duringRun = instant != null
                && !instant.isBefore(startedAt.minus(CLOCK_TOLERANCE))
                && !instant.isAfter(finishedAt.plus(CLOCK_TOLERANCE));
            matcher.appendReplacement(masked, Matcher.quoteReplacement(duringRun ? "[now]" : matcher.group()));
        }
        matcher.appendTail(masked);
        return masked.toString();
    }

    private static Instant toInstant(String timestamp) {
        try {
            if (timestamp.endsWith("Z") || timestamp.matches(".*[+-]\\d{2}:\\d{2}$")) {
                return OffsetDateTime.parse(timestamp).toInstant();
            }
            return LocalDateTime.parse(timestamp).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Path goldenDirectory(String document) {
        return GOLDENS.resolve(document.substring(0, document.length() - ".xml".length()));
    }

    private static String describe(Throwable error) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        Throwable current = error;
        while (current != null && seen.putIfAbsent(current.toString(), true) == null) {
            current = current.getCause();
        }
        return String.join("\n  caused by: ", seen.keySet());
    }
}
