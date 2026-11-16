package de.uniwue.zpd.dachs.larex.backend.service.annotation.corpus;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PageCorpusTest {

    private static final SortedMap<String, List<String>> observedDifferences = new ConcurrentSkipListMap<>();

    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private final PageCorpus corpus = new PageCorpus();

    @TempDir
    Path workDirectory;

    static List<String> documents() {
        return PageCorpus.documents();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documents")
    void pipelineOutputMatchesGoldens(String document) throws Exception {
        SortedMap<String, String> actual = PageCorpus.goldenFiles(corpus.run(document, workDirectory));
        if (PageCorpus.UPDATE) {
            PageCorpus.storeGoldenFiles(document, actual);
            return;
        }
        SortedMap<String, String> expected = PageCorpus.storedGoldenFiles(document);
        assertEquals(expected.keySet(), actual.keySet(), "Golden files of " + document);
        for (String name : expected.keySet()) {
            assertEquals(expected.get(name), actual.get(name), document + ": " + name);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documents")
    void pipelineDifferencesAreKnown(String document) throws Exception {
        List<String> actual = PageCorpus.differences(corpus.run(document, workDirectory));
        if (PageCorpus.UPDATE) {
            observedDifferences.put(document, actual);
            return;
        }
        List<String> expected = PageCorpus.knownDifferences().getOrDefault(document, List.of());
        assertEquals(String.join("\n", expected), String.join("\n", actual), "Differences of " + document);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documents")
    void olderVersionOpensLikeItsUpgrade(String document) throws Exception {
        PageCorpus.Result result = corpus.run(document, workDirectory);
        assumeTrue(result.versionChanged() && result.dto() != null, "not an older document that opens");

        Path source = workDirectory.resolve("source.xml");
        Files.writeString(source, result.source(), StandardCharsets.UTF_8);
        PageDto direct = new PageXmlToAnnotationParser(new Page4jToDtoMapper()).parse(source, null);

        assertEquals(JSON.writeValueAsString(result.dto()), JSON.writeValueAsString(direct), document);
    }

    @Test
    void knownDifferencesOnlyNameCorpusDocuments() throws Exception {
        TreeSet<String> listed = new TreeSet<>(PageCorpus.knownDifferences().keySet());
        listed.removeAll(PageCorpus.documents());
        assertEquals(new TreeSet<String>(), listed, "Documents listed in known-differences.txt but missing from the corpus");
    }

    @AfterAll
    static void storeObservedDifferences() throws Exception {
        if (!PageCorpus.UPDATE || observedDifferences.isEmpty()) {
            return;
        }
        SortedMap<String, List<String>> merged = PageCorpus.knownDifferences();
        merged.keySet().retainAll(PageCorpus.documents());
        merged.putAll(observedDifferences);
        PageCorpus.storeKnownDifferences(merged);
    }
}
