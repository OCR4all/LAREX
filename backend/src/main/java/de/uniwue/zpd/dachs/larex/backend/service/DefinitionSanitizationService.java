package de.uniwue.zpd.dachs.larex.backend.service;

import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.repository.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.util.JsonNodeUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class DefinitionSanitizationService {

    private final TagSetRepository tagSetRepository;
    private final LabelSetRepository labelSetRepository;

    public DefinitionSanitizationService(TagSetRepository tagSetRepository, LabelSetRepository labelSetRepository) {
        this.tagSetRepository = tagSetRepository;
        this.labelSetRepository = labelSetRepository;
    }

    @PostConstruct
    public void sanitizeIconFields() {
        sanitizeTagSets();
        sanitizeLabelSets();
    }

    private void sanitizeTagSets() {
        List<TagSet> tagSets = tagSetRepository.findAll();
        for (TagSet tagSet : tagSets) {
            JsonNodeUtils.SanitizationResult result = JsonNodeUtils.removeFieldRecursively(tagSet.getDefinition(), "icon");
            if (result.changed()) {
                tagSet.setDefinition(result.node());
                tagSetRepository.save(tagSet);
            }
        }
    }

    private void sanitizeLabelSets() {
        List<LabelSet> labelSets = labelSetRepository.findAll();
        for (LabelSet labelSet : labelSets) {
            JsonNodeUtils.SanitizationResult result = JsonNodeUtils.removeFieldRecursively(labelSet.getDefinition(), "icon");
            if (result.changed()) {
                labelSet.setDefinition(result.node());
                labelSetRepository.save(labelSet);
            }
        }
    }
}

