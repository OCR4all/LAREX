package de.uniwue.web.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.primaresearch.dla.page.Page;

/**
 * @author u.hartwig
 */
public class TestPageXMLReader {

	/**
	 * Ensure Legacy validated Transkribus format read as expected
	 */
	@Test
	public void testReadTranskribusPAGE2013() {
		// arrange
		String relativePath = "src/test/resources/xml/1744764298_18280403.gt.xml";
		Path pageTranskribus = Paths.get(relativePath);
		assertTrue(Files.exists(pageTranskribus));

		// act
		Page readPage = PageXMLReader.readPAGE(pageTranskribus.toFile());

		// assert
		assertNotNull(readPage);
		assertEquals(34, readPage.getLayout().getRegionCount());
	}

	/**
	 * Test behavior if invalid sourceFilename=Null used
	 */
	@Test
	public void testNullpointerInputFile() {

		assertNull(PageXMLReader.readPAGE(null));
	}

}
