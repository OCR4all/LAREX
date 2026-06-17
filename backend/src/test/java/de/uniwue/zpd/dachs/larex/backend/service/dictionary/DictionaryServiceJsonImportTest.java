package de.uniwue.zpd.dachs.larex.backend.service.dictionary;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
