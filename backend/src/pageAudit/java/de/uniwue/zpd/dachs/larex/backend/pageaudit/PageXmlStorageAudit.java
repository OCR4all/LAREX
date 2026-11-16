package de.uniwue.zpd.dachs.larex.backend.pageaudit;

import com.maxnth.page4j.convert.Conversion;
import com.maxnth.page4j.convert.ConversionMessage;
import com.maxnth.page4j.convert.PageConverter;
import com.maxnth.page4j.pagexml.PageReadException;
import com.maxnth.page4j.pagexml.PageXmlReadResult;
import com.maxnth.page4j.pagexml.PageXmlVersion;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.corpus.XmlDiff;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.exporter.AnnotationToPageXmlExporter;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class PageXmlStorageAudit {

    private static final String PAGE_NAMESPACE_PREFIX = "http://schema.primaresearch.org/PAGE/gts/pagecontent/";
    private static final Pattern QUOTED = Pattern.compile("'[^']*'|\"[^\"]*\"");
    private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?");
    private static final Pattern ID_STEP = Pattern.compile("\\[@id='[^']*']");
    private static final Pattern POSITION_STEP = Pattern.compile("\\[\\d+]");
    private static final int EXAMPLES = 3;

    private final PageXmlToAnnotationParser parser = new PageXmlToAnnotationParser(new Page4jToDtoMapper());
    private final AnnotationToPageXmlExporter exporter = new AnnotationToPageXmlExporter(new DtoToPage4jMapper());
    private final com.maxnth.page4j.pagexml.PageXml page4j = com.maxnth.page4j.pagexml.PageXml.create();

    private final Map<String, Tally> versions = new TreeMap<>();
    private final Map<String, Tally> outcomes = new TreeMap<>();
    private final Map<String, Tally> legacyFailures = new TreeMap<>();
    private final Map<String, Tally> strictFailures = new TreeMap<>();
    private final Map<String, Tally> legacyRoundTrip = new TreeMap<>();
    private final Map<String, Tally> strictRoundTrip = new TreeMap<>();
    private final Map<String, Tally> conversionTo2019 = new TreeMap<>();
    private final Map<String, Tally> conversionTo2024 = new TreeMap<>();
    private final Map<String, Tally> writeFailures = new TreeMap<>();
    private final List<String> regressions = new ArrayList<>();
    private final Map<String, FileResult> results = new TreeMap<>();
    private final Map<String, Tally> baselineOutcomeChanges = new TreeMap<>();
    private final Map<String, Tally> baselineNewDifferences = new TreeMap<>();
    private final Map<String, Tally> baselineFixedDifferences = new TreeMap<>();
    private final List<String> baselineNotes = new ArrayList<>();
    private int pageFiles;
    private int otherXmlFiles;

    public static void main(String[] args) throws Exception {
        Path directory = null;
        Path reportFile = null;
        Path baselineFile = null;
        for (String arg : args) {
            if (arg.startsWith("--report=")) {
                reportFile = Path.of(arg.substring("--report=".length())).toAbsolutePath();
            } else if (arg.startsWith("--baseline=")) {
                baselineFile = Path.of(arg.substring("--baseline=".length())).toAbsolutePath();
            } else {
                directory = Path.of(arg).toAbsolutePath().normalize();
            }
        }
        if (directory == null) {
            System.err.println("Usage: PageXmlStorageAudit <directory> [--report=<file>] [--baseline=<file>]");
            System.exit(2);
        }
        PageXmlStorageAudit audit = new PageXmlStorageAudit();
        Instant started = Instant.now();
        audit.run(directory);
        if (baselineFile != null) {
            audit.baseline(baselineFile);
        }
        String report = audit.report(directory, Duration.between(started, Instant.now()));
        if (reportFile != null) {
            Files.createDirectories(reportFile.getParent());
            Files.writeString(reportFile, report, StandardCharsets.UTF_8);
            System.out.println("Report written to " + reportFile);
        } else {
            System.out.println(report);
        }
    }

    private void run(Path directory) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(directory)) {
            files = walk.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".xml"))
                .sorted()
                .toList();
        }
        int done = 0;
        for (Path file : files) {
            audit(directory, file);
            if (++done % 100 == 0) {
                System.out.printf("%d / %d files%n", done, files.size());
            }
        }
    }

    private void audit(Path directory, Path file) {
        String name = directory.relativize(file).toString();
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            count(legacyFailures, "unreadable: " + e, name);
            return;
        }
        String namespace = rootNamespace(bytes);
        if (namespace == null || !namespace.startsWith(PAGE_NAMESPACE_PREFIX)) {
            otherXmlFiles++;
            return;
        }
        pageFiles++;
        count(versions, namespace.substring(PAGE_NAMESPACE_PREFIX.length()), name);
        String source = new String(bytes, StandardCharsets.UTF_8);

        String legacy = auditLarex(file, name, source);
        String strict = auditStrict(bytes, name, source);
        count(outcomes, "LAREX " + legacy + " / page4j strict " + strict, name);
        if ("ok".equals(legacy) && !"ok".equals(strict)) {
            regressions.add(name);
        }
    }

    private String auditLarex(Path file, String name, String source) {
        FileResult result = new FileResult();
        results.put(name, result);
        PageDto dto;
        try {
            dto = parser.parse(file, null);
        } catch (Exception e) {
            count(legacyFailures, normalize(describe(e).replace(file.toString(), "<file>")), name);
            result.outcome = "fails";
            return result.outcome;
        }
        try {
            String export = exporter.export(dto, null);
            List<XmlDiff.Difference> differences = source.contains(PAGE_NAMESPACE_PREFIX + "2019-07-15")
                ? XmlDiff.compare(source, export)
                : XmlDiff.compareIgnoringElementNamespaces(source, export);
            for (XmlDiff.Difference difference : differences) {
                count(legacyRoundTrip, pattern(difference), name);
                result.differences.add(baselineLine(difference));
            }
        } catch (Exception e) {
            count(legacyFailures, "save: " + normalize(describe(e)), name);
            result.outcome = "loads, save fails";
            return result.outcome;
        }
        result.outcome = "ok";
        return result.outcome;
    }

    private static String baselineLine(XmlDiff.Difference difference) {
        String path = POSITION_STEP.matcher(difference.path()).replaceAll("");
        boolean timestamp = path.endsWith("Metadata/Created") || path.endsWith("Metadata/LastChange");
        XmlDiff.Difference stable = timestamp && difference.kind() == XmlDiff.Kind.CHANGED_TEXT
            ? new XmlDiff.Difference(difference.kind(), difference.path(), difference.expected(), "[now]")
            : difference;
        return stable.toString().replace("\t", "\\t").replace("\r", "\\r");
    }

    private void baseline(Path baselineFile) throws IOException {
        if (!Files.exists(baselineFile)) {
            Files.createDirectories(baselineFile.getParent());
            StringBuilder out = new StringBuilder();
            results.forEach((file, result) -> {
                out.append(file).append('\t').append(result.outcome).append('\n');
                result.differences.forEach(difference -> out.append('\t').append(difference).append('\n'));
            });
            Files.writeString(baselineFile, out, StandardCharsets.UTF_8);
            baselineNotes.add("Baseline written to " + baselineFile + " for " + results.size() + " files");
            return;
        }
        Map<String, FileResult> baseline = new TreeMap<>();
        FileResult currentFile = null;
        for (String line : Files.readAllLines(baselineFile, StandardCharsets.UTF_8)) {
            if (line.startsWith("\t")) {
                currentFile.differences.add(line.substring(1));
            } else if (!line.isEmpty()) {
                int tab = line.lastIndexOf('\t');
                currentFile = new FileResult();
                currentFile.outcome = line.substring(tab + 1);
                baseline.put(line.substring(0, tab), currentFile);
            }
        }
        baselineNotes.add("Compared with baseline " + baselineFile + " (" + baseline.size() + " files)");
        for (Map.Entry<String, FileResult> entry : baseline.entrySet()) {
            FileResult now = results.get(entry.getKey());
            if (now == null) {
                count(baselineOutcomeChanges, "file missing from this run", entry.getKey());
                continue;
            }
            FileResult before = entry.getValue();
            if (!before.outcome.equals(now.outcome)) {
                count(baselineOutcomeChanges, before.outcome + " -> " + now.outcome, entry.getKey());
            }
            Set<String> beforeDifferences = new HashSet<>(before.differences);
            Set<String> nowDifferences = new HashSet<>(now.differences);
            for (String difference : now.differences) {
                if (!beforeDifferences.contains(difference)) {
                    count(baselineNewDifferences, difference, entry.getKey());
                }
            }
            for (String difference : before.differences) {
                if (!nowDifferences.contains(difference)) {
                    count(baselineFixedDifferences, difference, entry.getKey());
                }
            }
        }
        for (String file : results.keySet()) {
            if (!baseline.containsKey(file)) {
                count(baselineOutcomeChanges, "file not in baseline", file);
            }
        }
    }

    private String auditStrict(byte[] bytes, String name, String source) {
        PageXmlReadResult read;
        try {
            read = page4j.read(bytes);
        } catch (PageReadException e) {
            List<String> errors = e.errors().isEmpty() ? List.of(String.valueOf(e.getMessage())) : e.errors();
            errors.stream().map(PageXmlStorageAudit::normalizePosition).distinct().forEach(error -> count(strictFailures, error, name));
            return "fails";
        } catch (Exception e) {
            count(strictFailures, "unexpected: " + normalize(describe(e)), name);
            return "fails";
        }

        try {
            String rewritten = new String(page4j.write(read.page(), read.version()), StandardCharsets.UTF_8);
            for (XmlDiff.Difference difference : XmlDiff.compare(source, rewritten)) {
                count(strictRoundTrip, pattern(difference), name);
            }
        } catch (Exception e) {
            count(writeFailures, "same version: " + normalize(describe(e)), name);
        }
        convert(read, PageXmlVersion.V2019_07_15, conversionTo2019, name);
        convert(read, PageXmlVersion.V2024_07_15, conversionTo2024, name);
        return "ok";
    }

    private void convert(PageXmlReadResult read, PageXmlVersion target, Map<String, Tally> messages, String name) {
        if (read.version() == target) {
            return;
        }
        try {
            Conversion conversion = PageConverter.convert(read.page(), target);
            for (ConversionMessage message : conversion.messages()) {
                count(messages, message.kind() + " " + normalize(message.message()), name);
            }
            page4j.write(conversion.page(), target);
        } catch (Exception e) {
            count(writeFailures, target.date() + ": " + normalize(describe(e)), name);
        }
    }

    private static String rootNamespace(byte[] bytes) {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
            XMLStreamReader reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        return "PcGts".equals(reader.getLocalName()) ? reader.getNamespaceURI() : null;
                    }
                }
                return null;
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String pattern(XmlDiff.Difference difference) {
        String path = POSITION_STEP.matcher(ID_STEP.matcher(difference.path()).replaceAll("")).replaceAll("");
        String values = switch (difference.kind()) {
            case CHANGED_ATTRIBUTE, CHANGED_TEXT -> " " + shape(difference.expected()) + " -> " + shape(difference.actual())
                + numericChange(difference.expected(), difference.actual());
            default -> "";
        };
        return difference.kind() + " " + path + values;
    }

    private static String numericChange(String expected, String actual) {
        try {
            BigDecimal before = new BigDecimal(expected.strip());
            BigDecimal after = new BigDecimal(actual.strip());
            if (before.compareTo(after) == 0) {
                return " (same value)";
            }
            if (Double.parseDouble(expected.strip()) == Double.parseDouble(actual.strip())) {
                return " (same double, different decimal value)";
            }
            return " (VALUE CHANGED)";
        } catch (NumberFormatException | NullPointerException e) {
            return "";
        }
    }

    private static String shape(String value) {
        if (value == null) {
            return "null";
        }
        if (value.isEmpty()) {
            return "\"\"";
        }
        if (!value.strip().equals(value)) {
            return "<padded>";
        }
        if (NUMBER.matcher(value).matches()) {
            return value.contains(".") ? "<decimal>" : "<integer>";
        }
        return "<text>";
    }

    private static String normalizePosition(String message) {
        return message.replaceFirst("^\\d+:\\d+: ", "");
    }

    private static String normalize(String message) {
        return NUMBER.matcher(QUOTED.matcher(message).replaceAll("'…'")).replaceAll("N");
    }

    private static String describe(Throwable error) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        Throwable current = error;
        while (current != null && seen.putIfAbsent(current.toString(), true) == null) {
            current = current.getCause();
        }
        return String.join(" <- ", seen.keySet());
    }

    private static void count(Map<String, Tally> tallies, String key, String file) {
        tallies.computeIfAbsent(key, ignored -> new Tally()).add(file);
    }

    private String report(Path directory, Duration duration) {
        StringWriter buffer = new StringWriter();
        PrintWriter out = new PrintWriter(buffer);
        out.printf("PAGE XML storage audit: LAREX load and save (this build) next to page4j 1.0 (strict read, rewrite, conversion)%n");
        out.printf("Directory: %s%n", directory);
        out.printf("PAGE files: %d, other XML files skipped: %d, duration: %ds%n", pageFiles, otherXmlFiles, duration.toSeconds());
        section(out, "Versions", versions, false);
        section(out, "Outcomes", outcomes, true);
        baselineNotes.forEach(note -> out.printf("%s%n", note));
        out.printf("%n== Files LAREX loads and saves that the strict page4j 1.0 reader rejects: %d%n", regressions.size());
        regressions.forEach(file -> out.printf("  %s%n", file));
        section(out, "page4j 1.0 read errors", strictFailures, true);
        section(out, "LAREX load or save failures", legacyFailures, true);
        section(out, "LAREX load then save: differences from the stored file", legacyRoundTrip, true);
        if (!baselineNotes.isEmpty() && baselineNotes.getFirst().startsWith("Compared")) {
            section(out, "Baseline: changed outcomes", baselineOutcomeChanges, true);
            section(out, "Baseline: load then save differences that are NEW in this build", baselineNewDifferences, true);
            Map<String, Tally> fixedPatterns = new TreeMap<>();
            baselineFixedDifferences.forEach((difference, tally) -> tally.files.forEach(file -> count(fixedPatterns, difference.replaceAll("\\[@id='[^']*']|\\[\\d+]", "").replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"…\""), file)));
            section(out, "Baseline: load then save differences that this build no longer makes", fixedPatterns, true);
        }
        section(out, "page4j 1.0 read then write in the same version: differences from the stored file", strictRoundTrip, true);
        section(out, "page4j 1.0 conversion to 2019-07-15 (files in other versions)", conversionTo2019, true);
        section(out, "page4j 1.0 conversion to 2024-07-15", conversionTo2024, true);
        section(out, "page4j 1.0 write failures", writeFailures, true);
        out.flush();
        return buffer.toString();
    }

    private static void section(PrintWriter out, String title, Map<String, Tally> tallies, boolean examples) {
        out.printf("%n== %s%n", title);
        if (tallies.isEmpty()) {
            out.printf("  (none)%n");
            return;
        }
        tallies.entrySet().stream()
            .sorted(Comparator.comparing((Map.Entry<String, Tally> entry) -> entry.getValue().files.size()).reversed()
                .thenComparing(Map.Entry::getKey))
            .forEach(entry -> {
                Tally tally = entry.getValue();
                out.printf("  %6d files %7d times  %s%n", tally.files.size(), tally.occurrences, entry.getKey());
                if (examples) {
                    tally.files.stream().limit(EXAMPLES).forEach(file -> out.printf("                         e.g. %s%n", file));
                }
            });
    }

    private static final class FileResult {
        private String outcome;
        private final List<String> differences = new ArrayList<>();
    }

    private static final class Tally {
        private final List<String> files = new ArrayList<>();
        private int occurrences;

        void add(String file) {
            occurrences++;
            if (files.isEmpty() || !files.getLast().equals(file)) {
                files.add(file);
            }
        }
    }
}
