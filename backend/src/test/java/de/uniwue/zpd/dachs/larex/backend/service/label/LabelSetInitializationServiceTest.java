package de.uniwue.zpd.dachs.larex.backend.service.label;

import de.uniwue.zpd.dachs.larex.backend.dto.LabelSetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LabelSetInitializationServiceTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final LabelSetRepository repository = mock(LabelSetRepository.class);
    private final LabelSetInitializationService service = new LabelSetInitializationService(repository, json);

    @Test
    void newPageXmlLabelSetHasAPlainLabelForEveryRegionType() {
        when(repository.save(any(LabelSet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<LabelSetDto.Label> labels = labels(service.createPageXmlLabelset("workspace-1"));

        Set<LabelSetDto.PageRegionType> plain = EnumSet.noneOf(LabelSetDto.PageRegionType.class);
        for (LabelSetDto.Label label : labels) {
            LabelSetDto.PageXml pageXml = label.mapping().pageXml();
            if (pageXml.customSubType().isEmpty() && (pageXml.textType() == null || pageXml.textType().isEmpty())) {
                plain.add(pageXml.regionType());
            }
        }
        assertEquals(EnumSet.allOf(LabelSetDto.PageRegionType.class), plain);
        assertEquals(LabelSetDto.PageRegionType.CustomRegion, byName(labels, "Custom Region").mapping().pageXml().regionType());
        assertEquals(LabelSetDto.PageRegionType.FormRegion, byName(labels, "Form Region").mapping().pageXml().regionType());
    }

    @Test
    void existingPageXmlLabelSetGetsCustomRegionAndMissingRegionTypes() {
        LabelSet legacy = legacySystemSet();
        when(repository.findAll()).thenReturn(List.of(legacy));

        service.fixExistingLabelSets();

        ArgumentCaptor<LabelSet> saved = ArgumentCaptor.forClass(LabelSet.class);
        verify(repository).save(saved.capture());
        List<LabelSetDto.Label> labels = labels(saved.getValue());

        LabelSetDto.Label custom = byName(labels, "Custom Region");
        assertEquals("page-region-4", custom.id());
        assertEquals(LabelSetDto.PageRegionType.CustomRegion, custom.mapping().pageXml().regionType());
        assertEquals("", custom.mapping().pageXml().customSubType());
        assertEquals(LabelSetDto.PageRegionType.FormRegion, byName(labels, "Form Region").mapping().pageXml().regionType());
        assertEquals("page-text-subtype-5", labels.getLast().id());
        Set<String> ids = new HashSet<>();
        labels.forEach(label -> assertTrue(ids.add(label.id()), "duplicate id " + label.id()));
        Set<LabelSetDto.PageRegionType> regionTypes = EnumSet.noneOf(LabelSetDto.PageRegionType.class);
        labels.forEach(label -> regionTypes.add(label.mapping().pageXml().regionType()));
        assertEquals(EnumSet.allOf(LabelSetDto.PageRegionType.class), regionTypes);

        when(repository.findAll()).thenReturn(List.of(saved.getValue()));
        service.fixExistingLabelSets();
        verify(repository, times(1)).save(any(LabelSet.class));
    }

    @Test
    void userLabelSetsKeepTheirMappings() {
        LabelSet copy = legacySystemSet();
        copy.setName("PAGE XML Standard (Copy)");
        copy.setSystem(false);
        when(repository.findAll()).thenReturn(List.of(copy));

        service.fixExistingLabelSets();

        verify(repository, never()).save(any(LabelSet.class));
    }

    private LabelSet legacySystemSet() {
        LabelSetDto.CreateOrUpdateRequest request = new LabelSetDto.CreateOrUpdateRequest(
                new LabelSetDto.Meta("PAGE XML Standard", "", List.of(), true),
                List.of(
                        label("page-region-1", "Text Region", LabelSetDto.PageRegionType.TextRegion, null, "", "#4CAF50", "Text"),
                        label("page-region-2", "Image Region", LabelSetDto.PageRegionType.ImageRegion, null, "", "#FF5722", null),
                        label("page-region-3", "Unknown Region", LabelSetDto.PageRegionType.UnknownRegion, null, "", "#757575", null),
                        label("page-region-4", "Custom Region", LabelSetDto.PageRegionType.UnknownRegion, null, "custom", "#00BFA5", null),
                        label("page-text-subtype-5", "Paragraph", LabelSetDto.PageRegionType.TextRegion, "paragraph", "", "#1E88E5", "Text")
                )
        );
        LabelSet labelSet = new LabelSet("workspace-1", "PAGE XML Standard", "", json.valueToTree(request));
        labelSet.setSystem(true);
        return labelSet;
    }

    private static LabelSetDto.Label label(String id, String name, LabelSetDto.PageRegionType regionType, String textType,
                                           String customSubType, String color, String group) {
        return new LabelSetDto.Label(id, LabelSetDto.LabelScope.REGION, name, "", color,
                regionType == LabelSetDto.PageRegionType.TextRegion, false, group,
                new LabelSetDto.Mapping(new LabelSetDto.PageXml(regionType, textType, customSubType, "structure", "")));
    }

    private List<LabelSetDto.Label> labels(LabelSet labelSet) {
        return json.convertValue(labelSet.getDefinition(), LabelSetDto.CreateOrUpdateRequest.class).labels();
    }

    private static LabelSetDto.Label byName(List<LabelSetDto.Label> labels, String name) {
        return labels.stream().filter(label -> label.name().equals(name)).findFirst().orElseThrow();
    }
}
