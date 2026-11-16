package de.uniwue.zpd.dachs.larex.backend.service.export;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ExportWarningsHeaderTest {

    @Test
    void encodesLinesSoTheBrowserCanDecodeThem() {
        HttpHeaders headers = new HttpHeaders();

        ExportWarningsHeader.add(headers, List.of("FormRegion 'f1': changed to CustomRegion", "Größe: ä+ö 100%"));

        String value = headers.getFirst(ExportWarningsHeader.NAME);
        assertFalse(value.contains(" ") || value.contains("+") || value.contains("\n"));
        assertEquals("FormRegion 'f1': changed to CustomRegion\nGröße: ä+ö 100%", URLDecoder.decode(value, StandardCharsets.UTF_8));
    }

    @Test
    void keepsTheFirstLinesAndCountsTheRest() {
        List<String> warnings = IntStream.rangeClosed(1, 13).mapToObj(index -> "change " + index).toList();

        List<String> lines = List.of(URLDecoder.decode(ExportWarningsHeader.encode(warnings), StandardCharsets.UTF_8).split("\n"));

        assertEquals(ExportWarningsHeader.MAX_LINES + 1, lines.size());
        assertEquals("change 10", lines.get(ExportWarningsHeader.MAX_LINES - 1));
        assertEquals("and 3 more changes", lines.getLast());
    }

    @Test
    void addsNoHeaderWithoutWarnings() {
        HttpHeaders headers = new HttpHeaders();

        ExportWarningsHeader.add(headers, List.of());

        assertFalse(headers.containsHeader(ExportWarningsHeader.NAME));
    }
}
