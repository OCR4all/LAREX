package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapper;

import com.maxnth.page4j.model.Page;
import com.maxnth.page4j.model.Relation;
import com.maxnth.page4j.model.value.RelationType;
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
            null,
            null,
            null,
            null
        );
        Page page = mapper.toPage4j(dto);

        assertNotNull(page);
        assertEquals(1, page.relations().size());

        Relation relation = page.relations().getFirst();
        assertEquals("rel-1", relation.id());
        assertEquals(RelationType.JOIN, relation.type());
        assertEquals("r1", relation.sourceRegionRef());
        assertEquals("r2", relation.targetRegionRef());
        assertEquals("custom-field", relation.custom());
        assertEquals("relation-comment", relation.comments());
        assertEquals("caption", relation.labels().getFirst().labels().getFirst().value());
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
