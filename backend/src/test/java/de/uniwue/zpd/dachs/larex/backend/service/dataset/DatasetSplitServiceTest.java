package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import de.uniwue.zpd.dachs.larex.backend.entity.Dataset;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetSplitServiceTest {

    private final DatasetSplitService service = new DatasetSplitService();

    @Test
    void randomSeededAssignmentUsesConfiguredTargetCounts() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_VAL_TEST, Dataset.SplitAlgorithm.RANDOM_SEEDED);
        List<DatasetItem> items = items(10);

        List<String> warnings = service.regenerateSplitAssignments(dataset, items);

        assertThat(warnings).isEmpty();
        assertThat(countsBySplit(items)).containsEntry(DatasetItem.Split.TRAIN, 7L);
        assertThat(countsBySplit(items)).containsEntry(DatasetItem.Split.VAL, 2L);
        assertThat(countsBySplit(items)).containsEntry(DatasetItem.Split.TEST, 1L);
    }

    @Test
    void trainValTemplateNormalizesTestSplitToValidation() {
        assertThat(service.normalizeSplitForTemplate(Dataset.SplitTemplate.TRAIN_VAL, DatasetItem.Split.TEST))
                .isEqualTo(DatasetItem.Split.VAL);
    }

    @Test
    void multilabelStratifiedWithoutTagsWarnsAndFallsBackToRandom() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_VAL_TEST, Dataset.SplitAlgorithm.MULTILABEL_STRATIFIED_BY_TAGS);
        dataset.setStratifyTagIds(List.of());
        List<DatasetItem> items = items(4);

        List<String> warnings = service.regenerateSplitAssignments(dataset, items);

        assertThat(warnings).containsExactly("Multilabel stratified splitting requested without stratify tags. Falling back to random seeded assignment.");
        assertThat(items).allSatisfy(item -> assertThat(item.getAssignedSplit()).isNotNull());
    }

    @Test
    void invalidPercentagesAreRejected() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_VAL_TEST, Dataset.SplitAlgorithm.RANDOM_SEEDED);
        dataset.setTrainPercentage(50);
        dataset.setValPercentage(25);
        dataset.setTestPercentage(10);

        assertThatThrownBy(() -> service.validateSplitConfiguration(dataset))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("add up to 100");
    }

    private Dataset dataset(Dataset.SplitTemplate template, Dataset.SplitAlgorithm algorithm) {
        Dataset dataset = new Dataset();
        dataset.setSplitTemplate(template);
        dataset.setSplitAlgorithm(algorithm);
        dataset.setSplitSeed(42L);
        dataset.setTrainPercentage(template == Dataset.SplitTemplate.TRAIN_VAL ? 80 : 70);
        dataset.setValPercentage(template == Dataset.SplitTemplate.TRAIN_VAL ? 20 : 15);
        dataset.setTestPercentage(template == Dataset.SplitTemplate.TRAIN_VAL ? 0 : 15);
        return dataset;
    }

    private List<DatasetItem> items(int count) {
        List<DatasetItem> items = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            DatasetItem item = new DatasetItem();
            item.setSourceProjectId("project-" + (index % 2));
            item.setSourcePageTags(List.of(index % 2 == 0 ? "even" : "odd"));
            item.setMode(DatasetItem.Mode.LINK);
            items.add(item);
        }
        return items;
    }

    private Map<DatasetItem.Split, Long> countsBySplit(List<DatasetItem> items) {
        return items.stream().collect(Collectors.groupingBy(DatasetItem::getAssignedSplit, Collectors.counting()));
    }
}
