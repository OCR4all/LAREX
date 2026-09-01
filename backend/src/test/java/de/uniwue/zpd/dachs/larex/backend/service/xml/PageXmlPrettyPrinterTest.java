package de.uniwue.zpd.dachs.larex.backend.service.xml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageXmlPrettyPrinterTest {

    @Test
    void prettyPrint_indentsCompactPageXmlWithTwoSpaces() throws Exception {
        String formatted = PageXmlPrettyPrinter.prettyPrint(
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?><PcGts><Metadata><Creator>tester</Creator></Metadata><Page imageFilename=\"page.png\"/></PcGts>"
        );

        assertTrue(formatted.contains("\n  <Metadata>\n    <Creator>tester</Creator>\n  </Metadata>"));
        assertTrue(formatted.contains("\n  <Page imageFilename=\"page.png\"/>\n</PcGts>"));
        assertTrue(formatted.endsWith("\n"));
    }

    @Test
    void prettyPrint_isIdempotentAndPreservesWhitespaceOnlyTextContent() throws Exception {
        String compact = "<PcGts><Page><TextRegion><TextEquiv><Unicode>   </Unicode></TextEquiv></TextRegion></Page></PcGts>";

        String once = PageXmlPrettyPrinter.prettyPrint(compact);
        String twice = PageXmlPrettyPrinter.prettyPrint(once);

        assertEquals(once, twice);
        assertTrue(once.contains("<Unicode>   </Unicode>"));
    }

    @Test
    void prettyPrint_rejectsDoctypeDeclarations() {
        String xml = "<!DOCTYPE PcGts [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]><PcGts>&xxe;</PcGts>";

        assertThrows(Exception.class, () -> PageXmlPrettyPrinter.prettyPrint(xml));
    }
}
