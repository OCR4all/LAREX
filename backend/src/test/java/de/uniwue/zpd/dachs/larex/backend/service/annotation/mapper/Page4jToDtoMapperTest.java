package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapper;

import com.maxnth.page4j.basic.labels.LabelGroup;
import com.maxnth.page4j.basic.labels.LabelImpl;
import com.maxnth.page4j.basic.labels.Labels;
import com.maxnth.page4j.basic.variable.VariableValue;
import com.maxnth.page4j.dla.page.Page;
import com.maxnth.page4j.dla.page.io.xml.DefaultXmlNames;
import com.maxnth.page4j.dla.page.io.xml.PageXmlInputOutput;
import com.maxnth.page4j.dla.page.layout.logical.ContentObjectRelation;
import com.maxnth.page4j.dla.page.layout.physical.shared.RegionType;
import com.maxnth.page4j.dla.page.layout.physical.text.TextContent;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.TextLine;
import com.maxnth.page4j.dla.page.layout.physical.text.impl.TextRegion;
import com.maxnth.page4j.maths.geometry.Polygon;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlPresenceIndex;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import org.junit.jupiter.api.Test;


import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class Page4jToDtoMapperTest {

    @Test
    void toDto_omitsDefaultedTextRegionTypeAndMetadataWithoutPresence() throws Exception {
        Page page = createPageWithTextRegion();
        page.getMetaData().setCreationTime(new Date(0L));
        page.getMetaData().setLastModifiedTime(new Date(0L));

        Page4jToDtoMapper mapper = new Page4jToDtoMapper();
        var dto = mapper.toDto(page, PageXmlPresenceIndex.empty());

        assertNotNull(dto);
        assertNotNull(dto.regions());
        assertEquals(1, dto.regions().size());
        assertNull(dto.regions().get(0).type());
        assertNull(dto.regions().get(0).continuation());
        assertNull(dto.metadata());
    }

    @Test
    void toDto_keepsMetadataAndTextRegionTypeWhenPresentInSourceXml() throws Exception {
        Page page = createPageWithTextRegion();
        page.getMetaData().setCreator("tester");
        page.getMetaData().setComments("comment");
        page.getMetaData().setExternalRef("ext");
        page.getMetaData().setCreationTime(Date.from(LocalDateTime.of(2025, 1, 1, 10, 15).atZone(ZoneId.systemDefault()).toInstant()));
        page.getMetaData().setLastModifiedTime(Date.from(LocalDateTime.of(2025, 1, 1, 11, 30).atZone(ZoneId.systemDefault()).toInstant()));

        PageXmlPresenceIndex presence = new PageXmlPresenceIndex(
            Set.of(),
            Map.of("r1", Set.of("id", "type", "continuation")),
            true,
            true,
            true,
            true,
            true,
            Set.of("r1"),
            Map.of(),
            Set.of()
        );

        Page4jToDtoMapper mapper = new Page4jToDtoMapper();
        var dto = mapper.toDto(page, presence);

        assertNotNull(dto);
        assertEquals("paragraph", dto.regions().get(0).type());
        assertEquals(false, dto.regions().get(0).continuation());
        assertNotNull(dto.metadata());
        assertEquals("tester", dto.metadata().creator());
        assertEquals("comment", dto.metadata().comments());
        assertEquals("ext", dto.metadata().externalRef());
    }

    @Test
    void toDto_keepsMissingTextEquivConfidenceAsNull() throws Exception {
        Page page = createPageWithTextRegion();
        TextRegion textRegion = (TextRegion) page.getLayout().getRegion(0);
        TextLine textLine = textRegion.createTextLine("tl1");
        textLine.setText("without-conf");
        TextContent withConfidence = textLine.addTextContentVariant();
        withConfidence.setText("with-conf");
        withConfidence.setConfidence(0.87);

        PageXmlPresenceIndex presence = new PageXmlPresenceIndex(
            Set.of(),
            Map.of(),
            false,
            false,
            false,
            false,
            false,
            Set.of(),
            Map.of("tl1", Set.of(1)),
            Set.of()
        );
        Page4jToDtoMapper mapper = new Page4jToDtoMapper();
        var dto = mapper.toDto(page, presence);

        assertNotNull(dto);
        assertNotNull(dto.regions());
        assertNotNull(dto.regions().get(0).textLines());
        assertNotNull(dto.regions().get(0).textLines().get(0).textContentVariants());
        assertEquals(2, dto.regions().get(0).textLines().get(0).textContentVariants().size());
        assertNull(dto.regions().get(0).textLines().get(0).textContentVariants().get(0).confidence());
        assertEquals(0.87, dto.regions().get(0).textLines().get(0).textContentVariants().get(1).confidence());
    }

    @Test
    void toDto_exportsRelationsWithFieldsAndLabels() throws Exception {
        Page page = createPageWithTextRegion();
        TextRegion source = (TextRegion) page.getLayout().getRegion(0);

        TextRegion target = (TextRegion) page.getLayout().createRegion(RegionType.TextRegion, "r2");
        Polygon polygon = new Polygon();
        polygon.addPoint(200, 0);
        polygon.addPoint(300, 0);
        polygon.addPoint(300, 100);
        polygon.addPoint(200, 100);
        target.setCoords(polygon);
        target.getAttributes().get(DefaultXmlNames.ATTR_type).setValue(VariableValue.of("paragraph"));

        ContentObjectRelation relation = page.getLayout().getRelations().addRelation(
            source,
            target,
            ContentObjectRelation.RelationType.Join,
            "rel-1"
        );
        assertNotNull(relation);
        relation.setCustomField("custom-field");
        relation.setComments("relation-comment");
        Labels labels = new Labels();
        LabelGroup group = new LabelGroup("model-a");
        LabelImpl label = new LabelImpl("caption", group.getExternalModel());
        label.setType("semantic");
        label.setComments("label-comment");
        group.addLabel(label);
        labels.addGroup(group);
        relation.setLabels(labels);

        Page4jToDtoMapper mapper = new Page4jToDtoMapper();
        var dto = mapper.toDto(page, PageXmlPresenceIndex.empty());

        assertNotNull(dto.relations());
        assertEquals(1, dto.relations().relations().size());
        assertEquals("rel-1", dto.relations().relations().get(0).id());
        assertEquals("join", dto.relations().relations().get(0).type());
        assertEquals("r1", dto.relations().relations().get(0).sourceRegionRef());
        assertEquals("r2", dto.relations().relations().get(0).targetRegionRef());
        assertEquals("custom-field", dto.relations().relations().get(0).custom());
        assertEquals("relation-comment", dto.relations().relations().get(0).comments());
        assertNotNull(dto.relations().relations().get(0).labels());
        assertEquals("caption", dto.relations().relations().get(0).labels().get(0).labels().get(0).value());
    }

    private Page createPageWithTextRegion() throws Exception {
        Page page = new Page(PageXmlInputOutput.getLatestSchemaModel());
        page.setImageFilename("image.png");
        page.getLayout().setSize(1200, 1800);

        TextRegion textRegion = (TextRegion) page.getLayout().createRegion(RegionType.TextRegion, "r1");
        Polygon polygon = new Polygon();
        polygon.addPoint(0, 0);
        polygon.addPoint(100, 0);
        polygon.addPoint(100, 100);
        polygon.addPoint(0, 100);
        textRegion.setCoords(polygon);
        textRegion.getAttributes().get(DefaultXmlNames.ATTR_type).setValue(VariableValue.of("paragraph"));
        textRegion.getAttributes().get(DefaultXmlNames.ATTR_continuation).setValue(VariableValue.of(false));

        return page;
    }
}
