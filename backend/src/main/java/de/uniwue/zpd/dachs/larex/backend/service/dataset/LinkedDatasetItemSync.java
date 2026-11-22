package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.PageXml;
import de.uniwue.zpd.dachs.larex.backend.repository.dataset.DatasetItemRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
public class LinkedDatasetItemSync {

    public static final String MISSING_SOURCE_XML_REASON = "Selected source annotation is missing.";

    private final DatasetItemRepository datasetItemRepository;
    private final PageRepository pageRepository;

    public LinkedDatasetItemSync(DatasetItemRepository datasetItemRepository, PageRepository pageRepository) {
        this.datasetItemRepository = datasetItemRepository;
        this.pageRepository = pageRepository;
    }

    public void removeForPages(Collection<String> pageIds) {
        if (pageIds == null || pageIds.isEmpty()) {
            return;
        }
        datasetItemRepository.deleteAll(datasetItemRepository.findBySourcePageIdInAndMode(pageIds, DatasetItem.Mode.LINK));
    }

    public void removeForProject(String projectId) {
        removeForPages(pageRepository.findIdsByProjectId(projectId));
    }

    public void relink(PageXml xml) {
        List<DatasetItem> changed = new ArrayList<>();
        for (DatasetItem item : datasetItemRepository.findBySourcePageIdInAndMode(List.of(xml.getPage().getId()), DatasetItem.Mode.LINK)) {
            boolean relinked = !xml.getId().equals(item.getSelectedSourceXmlId());
            if (!relinked && Objects.equals(xml.getFileName(), item.getSelectedSourceXmlFileName())) {
                continue;
            }
            item.setSelectedSourceXmlId(xml.getId());
            item.setSelectedSourceXmlFileName(xml.getFileName());
            item.setSelectedSourceXmlUpdatedAt(xml.getUpdated());
            if (relinked && item.getStatus() == DatasetItem.Status.BROKEN && MISSING_SOURCE_XML_REASON.equals(item.getBrokenReason())) {
                item.setStatus(DatasetItem.Status.READY);
                item.setBrokenReason(null);
            }
            changed.add(item);
        }
        if (!changed.isEmpty()) {
            datasetItemRepository.saveAll(changed);
        }
    }
}
