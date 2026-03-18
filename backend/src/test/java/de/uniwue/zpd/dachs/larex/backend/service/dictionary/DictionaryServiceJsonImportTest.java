package de.uniwue.zpd.dachs.larex.backend.service.dictionary;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DictionaryServiceJsonImportTest {

    @Test
    void parseJsonTreatsWordMapWithPackageLikeKeysAsDictionaryContent() throws Exception {
        DictionaryService service = new DictionaryService(
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

        Method entriesMethod = parsed.getClass().getDeclaredMethod("entries");
        @SuppressWarnings("unchecked")
        List<Object> entries = (List<Object>) entriesMethod.invoke(parsed);

        assertEquals(4, entries.size());

        Method formMethod = entries.getFirst().getClass().getDeclaredMethod("form");
        assertNotNull(formMethod);
        List<String> forms = entries.stream()
                .map(entry -> {
                    try {
                        return (String) formMethod.invoke(entry);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());

        assertEquals(List.of("resources", "entries", "apple", "pear"), forms);
    }
}
