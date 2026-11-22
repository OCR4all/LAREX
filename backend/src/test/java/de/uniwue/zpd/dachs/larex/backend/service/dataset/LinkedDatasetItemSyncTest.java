package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.Page;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.entity.XmlSchema;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkedDatasetItemSyncTest {

    @Mock
    private DatasetItemRepository datasetItemRepository;

    @Mock
    private PageRepository pageRepository;

    @Test
    void relinkPointsLinkedItemsAtTheReplacementXml() {
        PageXml xml = xml("xml-new", "page-new.xml");
        DatasetItem missingXml = item("xml-old", DatasetItem.Status.BROKEN, LinkedDatasetItemSync.MISSING_SOURCE_XML_REASON);
        DatasetItem missingImage = item("xml-old", DatasetItem.Status.BROKEN, "One or more selected source image variants are missing.");
        when(datasetItemRepository.findBySourcePageIdInAndMode(List.of("page-1"), DatasetItem.Mode.LINK))
                .thenReturn(List.of(missingXml, missingImage));

        new LinkedDatasetItemSync(datasetItemRepository, pageRepository).relink(xml);

        assertEquals("xml-new", missingXml.getSelectedSourceXmlId());
        assertEquals("page-new.xml", missingXml.getSelectedSourceXmlFileName());
        assertEquals(DatasetItem.Status.READY, missingXml.getStatus());
        assertNull(missingXml.getBrokenReason());
        assertEquals("xml-new", missingImage.getSelectedSourceXmlId());
        assertEquals(DatasetItem.Status.BROKEN, missingImage.getStatus());
        verify(datasetItemRepository).saveAll(List.of(missingXml, missingImage));
    }

    @Test
    void relinkUpdatesFileNameOfOverwrittenXml() {
        PageXml xml = xml("xml-1", "page-renamed.xml");
        DatasetItem item = item("xml-1", DatasetItem.Status.READY, null);
        when(datasetItemRepository.findBySourcePageIdInAndMode(List.of("page-1"), DatasetItem.Mode.LINK))
                .thenReturn(List.of(item));

        new LinkedDatasetItemSync(datasetItemRepository, pageRepository).relink(xml);

        assertEquals("page-renamed.xml", item.getSelectedSourceXmlFileName());
        verify(datasetItemRepository).saveAll(List.of(item));
    }

    @Test
    void relinkSkipsItemsAlreadyOnTheXml() {
        PageXml xml = xml("xml-1", "page-old.xml");
        DatasetItem current = item("xml-1", DatasetItem.Status.READY, null);
        when(datasetItemRepository.findBySourcePageIdInAndMode(List.of("page-1"), DatasetItem.Mode.LINK))
                .thenReturn(List.of(current));

        new LinkedDatasetItemSync(datasetItemRepository, pageRepository).relink(xml);

        verify(datasetItemRepository, never()).saveAll(any());
    }

    @Test
    void removeForProjectDeletesOnlyLinkedItemsOfItsPages() {
        DatasetItem linked = item("xml-1", DatasetItem.Status.READY, null);
        when(pageRepository.findIdsByProjectId("project-1")).thenReturn(List.of("page-1"));
        when(datasetItemRepository.findBySourcePageIdInAndMode(List.of("page-1"), DatasetItem.Mode.LINK))
                .thenReturn(List.of(linked));

        new LinkedDatasetItemSync(datasetItemRepository, pageRepository).removeForProject("project-1");

        verify(datasetItemRepository).deleteAll(List.of(linked));
    }

    private PageXml xml(String id, String fileName) {
        Page page = new Page();
        page.setId("page-1");
        PageXml xml = new PageXml(fileName, "path/" + fileName, "application/xml", 1L, "original", "page", XmlSchema.PAGE_XML, null, page);
        xml.setId(id);
        return xml;
    }

    private DatasetItem item(String xmlId, DatasetItem.Status status, String brokenReason) {
        DatasetItem item = new DatasetItem();
        item.setMode(DatasetItem.Mode.LINK);
        item.setSourcePageId("page-1");
        item.setSelectedSourceXmlId(xmlId);
        item.setSelectedSourceXmlFileName("page-old.xml");
        item.setStatus(status);
        item.setBrokenReason(brokenReason);
        return item;
    }
}
