package de.uniwue.zpd.dachs.larex.backend.service.character;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CharacterSearchServiceTest {

    @Test
    void isPua_detectsAllPuaRanges() {
        assertTrue(CharacterPua.isPua(0xE000));
        assertTrue(CharacterPua.isPua(0xF8FF));
        assertFalse(CharacterPua.isPua(0xDFFF));
        assertFalse(CharacterPua.isPua(0xF900));

        assertTrue(CharacterPua.isPua(0xF0000));
        assertTrue(CharacterPua.isPua(0xFFFFD));
        assertFalse(CharacterPua.isPua(0xFFFE));

        assertTrue(CharacterPua.isPua(0x100000));
        assertTrue(CharacterPua.isPua(0x10FFFD));
        assertFalse(CharacterPua.isPua(0x10FFFE));
    }

    @Test
    void unicodeLoader_parsesCoreFieldsAndSkipsRangePlaceholders() throws Exception {
        String unicode = "0041;LATIN CAPITAL LETTER A;Lu;0;L;;;;;N;;;;0061;\n"
                + "AC00;<Hangul Syllable, First>;Lo;0;L;;;;;N;;;;;\n";

        StringPool pool = new StringPool();
        List<CharacterEntry> entries = CharacterDataLoader.loadUnicodeData(
                new ByteArrayInputStream(unicode.getBytes(StandardCharsets.UTF_8)),
                pool
        );

        assertEquals(1, entries.size());
        CharacterEntry e = entries.getFirst();
        assertEquals(CharacterSource.UNICODE, e.source());
        assertEquals(0x0041, e.codePoint());
        assertEquals("LATIN CAPITAL LETTER A", e.description());
        assertEquals("Lu", e.generalCategory());
        assertFalse(e.isPua());
    }

    @Test
    void mufiLoader_parsesAndMergesDescriptions() throws Exception {
        String mufi = "[" +
                "{" +
                "\"codepoint\":\"0050\"," +
                "\"description\":\"LATIN CAPITAL LETTER P\"," +
                "\"descriptionalt\":\"ALT\"," +
                "\"range\":\"BasLat\"," +
                "\"mufiversion\":\"3.0\"," +
                "\"mufi_status\":\"accepted\"," +
                "\"deprecated\":\"0\"," +
                "\"url\":\"https://example.test/p\"" +
                "}," +
                "{" +
                "\"codepoint\":\"E000\"," +
                "\"description\":\"PUA TEST\"," +
                "\"range\":\"PUA\"," +
                "\"mufiversion\":\"3.0\"," +
                "\"mufi_status\":\"accepted\"," +
                "\"deprecated\":\"1\"" +
                "}" +
                "]";

        StringPool pool = new StringPool();
        List<CharacterEntry> entries = CharacterDataLoader.loadMufiData(
                new ByteArrayInputStream(mufi.getBytes(StandardCharsets.UTF_8)),
                pool
        );

        assertEquals(2, entries.size());

        CharacterEntry p = entries.getFirst();
        assertEquals(CharacterSource.MUFI, p.source());
        assertEquals(0x0050, p.codePoint());
        assertEquals("LATIN CAPITAL LETTER P / ALT", p.description());
        assertEquals("BasLat", p.mufiRange());
        assertEquals("3.0", p.mufiVersion());
        assertEquals("accepted", p.mufiStatus());
        assertFalse(p.deprecated());
        assertFalse(p.isPua());
        assertEquals("https://example.test/p", p.url());

        CharacterEntry pua = entries.get(1);
        assertEquals(0xE000, pua.codePoint());
        assertTrue(pua.isPua());
        assertTrue(pua.deprecated());
    }

    @Test
    void search_supportsFacetsAndFilters() {
        List<CharacterEntry> entries = List.of(
                new CharacterEntry(CharacterSource.UNICODE, 0x0041, "LATIN CAPITAL LETTER A", false, "Lu", null, null, null, false, null),
                new CharacterEntry(CharacterSource.UNICODE, 0x0061, "LATIN SMALL LETTER A", false, "Ll", null, null, null, false, null),
                new CharacterEntry(CharacterSource.MUFI, 0x0050, "LATIN CAPITAL LETTER P", false, null, "BasLat", "3.0", "accepted", false, null),
                new CharacterEntry(CharacterSource.MUFI, 0xE000, "PUA TEST", true, null, "PUA", "3.0", "accepted", true, null)
        );

        CharacterSearchService service = new CharacterSearchService();
        service.setIndexForTests(CharacterSearchService.Index.build(entries));

        CharacterSearchService.SearchResult res = service.search(
                "latin letter",
                0,
                50,
                EnumSet.allOf(CharacterSource.class),
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(3, res.total());
        assertEquals(3, res.items().size());

        assertEquals(Map.of("false", 3L), res.facets().get("isPua"));
        assertEquals(Map.of("unicode", 2L, "mufi", 1L), res.facets().get("source"));
        assertEquals(Map.of("Lu", 1L, "Ll", 1L), res.facets().get("generalCategory"));

        CharacterSearchService.SearchResult onlyPua = service.search(
                "test",
                0,
                50,
                Set.of(CharacterSource.MUFI),
                true,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(1, onlyPua.total());
        assertEquals(0xE000, onlyPua.items().getFirst().codePoint());
        assertEquals(Map.of("true", 1L), onlyPua.facets().get("isPua"));
        assertEquals(Map.of("mufi", 1L), onlyPua.facets().get("source"));
    }
}
