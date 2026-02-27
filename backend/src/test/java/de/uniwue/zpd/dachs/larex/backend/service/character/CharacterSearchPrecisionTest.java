package de.uniwue.zpd.dachs.larex.backend.service.character;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CharacterSearchPrecisionTest {

    @Test
    void andMatching_respectsSingleLetterTokens() {
        List<CharacterEntry> entries = List.of(
                new CharacterEntry(CharacterSource.UNICODE, 0x00C1, "LATIN CAPITAL LETTER A WITH ACUTE", false, "Lu", null, null, null, false, null),
                new CharacterEntry(CharacterSource.UNICODE, 0x00C9, "LATIN CAPITAL LETTER E WITH ACUTE", false, "Lu", null, null, null, false, null)
        );

        CharacterSearchService service = new CharacterSearchService();
        service.setIndexForTests(CharacterSearchService.Index.build(entries));

        CharacterSearchService.SearchResult res = service.search(
                "LATIN CAPITAL LETTER A WITH ACUTE",
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

        assertEquals(1, res.total());
        assertEquals("LATIN CAPITAL LETTER A WITH ACUTE", res.items().getFirst().description());
    }
}
