package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import de.uniwue.zpd.dachs.larex.backend.dto.DatasetDto;
import de.uniwue.zpd.dachs.larex.backend.dto.TagSetDto;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.service.tag.TagLookupService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasetTagCatalogTest {

    private final TagSetDto.TagNode fraktur = new TagSetDto.TagNode("tag-fraktur", "Fraktur", null, "#112233", List.of());
    private final TagSetDto.TagNode print = new TagSetDto.TagNode("tag-print", "Print", null, "#445566", List.of(fraktur));
    private final TagSetDto.TagNode rubric = new TagSetDto.TagNode("tag-rubric", "Rubric", null, null, List.of());

    @Test
    void itemTagsIncludeAncestorsFromTheProjectTagSet() {
        DatasetTagCatalog catalog = catalog();

        assertThat(catalog.tagsWithAncestors(item("project", "tag-fraktur"))).containsExactlyInAnyOrder("tag-fraktur", "tag-print");
        assertThat(catalog.tagsWithAncestors(item("other", "tag-fraktur"))).containsExactly("tag-fraktur");
    }

    @Test
    void optionsResolveNamesPathsAndCountPagesThroughChildTags() {
        DatasetTagCatalog catalog = catalog();
        List<DatasetItem> items = List.of(item("project", "tag-fraktur"), item("project", "tag-print"), item("project", "tag-rubric"));

        List<DatasetDto.TagOption> options = catalog.options(items, List.of("tag-deleted"));

        assertThat(options).containsExactly(
                new DatasetDto.TagOption("tag-print", "Print", List.of("Print"), "#445566", "Scripts", 2),
                new DatasetDto.TagOption("tag-fraktur", "Fraktur", List.of("Print", "Fraktur"), "#112233", "Scripts", 1),
                new DatasetDto.TagOption("tag-rubric", "Rubric", List.of("Rubric"), null, "Scripts", 1),
                new DatasetDto.TagOption("tag-deleted", "tag-deleted", List.of("tag-deleted"), null, null, 0)
        );
    }

    private DatasetTagCatalog catalog() {
        TagSet tagSet = new TagSet();
        tagSet.setId("tag-set");
        tagSet.setName("Scripts");
        Project project = new Project();
        project.setId("project");
        project.setTagSet(tagSet);

        Map<String, TagSetDto.TagNode> lookup = new HashMap<>();
        lookup.put(print.id(), print);
        lookup.put(fraktur.id(), fraktur);
        lookup.put(rubric.id(), rubric);
        TagLookupService tagLookupService = mock(TagLookupService.class);
        when(tagLookupService.buildTagLookupForProject(project)).thenReturn(lookup);
        return DatasetTagCatalog.of(List.of(project), tagLookupService);
    }

    private DatasetItem item(String projectId, String... tags) {
        DatasetItem item = new DatasetItem();
        item.setSourceProjectId(projectId);
        item.setSourcePageTags(List.of(tags));
        return item;
    }
}
