package de.uniwue.zpd.dachs.larex.backend.service.dictionary;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DictionaryServiceJsonImportTest {

    @Test
    void parseTxtTreatsContentAsWhitespaceSeparatedCorpus() throws Exception {
        DictionaryService service = dictionaryService();

        Method parseTxt = DictionaryService.class.getDeclaredMethod("parseTxt", String.class);
        parseTxt.setAccessible(true);

        Object parsed = parseTxt.invoke(service, """
                alpha beta

                gamma\tdelta
                # marker
                """);

        assertEquals(List.of("alpha", "beta", "gamma", "delta", "#", "marker"), forms(parsed));
    }

    @Test
    void parseJsonTreatsWordMapWithPackageLikeKeysAsDictionaryContent() throws Exception {
        DictionaryService service = dictionaryService();

        Method parseJson = DictionaryService.class.getDeclaredMethod("parseJson", String.class);
        parseJson.setAccessible(true);

        Object parsed = parseJson.invoke(service, """
                {
                  "resources": 1,
                  "entries": 2,
                  "apple": 1,
                  "pear": 1
                }
                """);

        assertEquals(List.of("resources", "entries", "apple", "pear"), forms(parsed));
    }

    @Test
    void parseTeiRejectsExternalEntities(@TempDir Path tempDir) throws Exception {
        Path secret = Files.writeString(tempDir.resolve("secret.txt"), "SECRET-CANARY");
        DictionaryService service = dictionaryService();

        Method parseTei = DictionaryService.class.getDeclaredMethod("parseTei", String.class);
        parseTei.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class, () -> parseTei.invoke(service, """
                <?xml version="1.0"?>
                <!DOCTYPE TEI [<!ENTITY xxe SYSTEM "%s">]>
                <TEI xmlns="http://www.tei-c.org/ns/1.0"><text><body>
                  <entry><form><orth>&xxe;</orth></form></entry>
                </body></text></TEI>
                """.formatted(secret.toUri())));

        assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
    }

    private DictionaryService dictionaryService() {
        return new DictionaryService(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper()
        );
    }

    private List<String> forms(Object parsed) throws Exception {
        Method entriesMethod = parsed.getClass().getDeclaredMethod("entries");
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) entriesMethod.invoke(parsed);

        Method formMethod = entries.getFirst().getClass().getDeclaredMethod("form");
        assertNotNull(formMethod);
        return entries.stream()
                .map(entry -> {
                    try {
                        return (String) formMethod.invoke(entry);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
    }
}
