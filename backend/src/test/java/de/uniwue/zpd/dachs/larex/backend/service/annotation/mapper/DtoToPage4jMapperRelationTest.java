package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapper;

import com.maxnth.page4j.dla.page.Page;
import com.maxnth.page4j.dla.page.layout.logical.ContentObjectRelation;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PointDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.PolygonDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.RelationDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.RelationsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.LabelDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.LabelsDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionKind;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DtoToPage4jMapperRelationTest {

    @Test
    void toPage4j_importsJoinRelationsAndSkipsUnresolvedEndpoints() {
        DtoToPage4jMapper mapper = new DtoToPage4jMapper();
        PageDto dto = new PageDto(
            "img.png",
            1000,
            1500,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(region("r1", -0.5), region("r2", 0.5)),
            null,
            null,
            null,
            null,
            null,
            null,
            new RelationsDto(List.of(
                new RelationDto(
                    "rel-1",
                    "join",
                    "r1",
                    "r2",
                    "custom-field",
                    "relation-comment",
                    List.of(new LabelsDto("model-a", null, null, null, List.of(
                        new LabelDto("caption", "semantic", "label-comment")
                    )))
                ),
                new RelationDto(
                    "rel-missing",
                    "link",
                    "r1",
                    "missing",
                    null,
                    null,
                    null
                )
            )),
            null
        );
        Page page = mapper.toPage4j(dto);

        assertNotNull(page);
        var relations = page.getLayout().getRelations().exportRelations();
        assertEquals(1, relations.size());

        ContentObjectRelation relation = relations.iterator().next();
        assertEquals("rel-1", relation.getId().toString());
        assertEquals(ContentObjectRelation.RelationType.Join, relation.getRelationType());
        assertEquals("r1", relation.getObject1().getId().toString());
        assertEquals("r2", relation.getObject2().getId().toString());
        assertEquals("custom-field", relation.getCustomField());
        assertEquals("relation-comment", relation.getComments());
        assertNotNull(relation.getLabels());
        assertEquals("caption", relation.getLabels().getGroups().values().iterator().next().getLabels().get(0).getValue());
    }

    private RegionDto region(String id, double offsetX) {
        return new RegionDto(
            id,
            RegionKind.TextRegion,
            rectangle(offsetX),
            null,
            null,
            "paragraph",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private PolygonDto rectangle(double offsetX) {
        return new PolygonDto(List.of(
            new PointDto(offsetX - 0.2, -0.2),
            new PointDto(offsetX + 0.2, -0.2),
            new PointDto(offsetX + 0.2, 0.2),
            new PointDto(offsetX - 0.2, 0.2)
        ), null);
    }
}
