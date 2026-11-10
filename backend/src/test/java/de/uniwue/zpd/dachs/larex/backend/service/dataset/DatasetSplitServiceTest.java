package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import de.uniwue.zpd.dachs.larex.backend.entity.Dataset;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
    void multilabelStratifiedWarnsWhenNoPageCarriesASelectedTag() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_VAL_TEST, Dataset.SplitAlgorithm.MULTILABEL_STRATIFIED_BY_TAGS);
        dataset.setStratifyTagIds(List.of("missing"));
        List<DatasetItem> items = items(10);

        List<String> warnings = service.regenerateSplitAssignments(dataset, items);

        assertThat(warnings).containsExactly("No page in this dataset carries any of the selected stratify tags. Falling back to random seeded assignment.");
        assertThat(countsBySplit(items)).containsEntry(DatasetItem.Split.TRAIN, 7L);
    }

    @Test
    void multilabelStratifiedKeepsSplitSizesAndBalancesEachTag() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_VAL_TEST, Dataset.SplitAlgorithm.MULTILABEL_STRATIFIED_BY_TAGS);
        dataset.setStratifyTagIds(List.of("print", "marginalia"));
        List<DatasetItem> items = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            List<String> tags = new ArrayList<>();
            if (index < 40) tags.add("print");
            if (index >= 30 && index < 50) tags.add("marginalia");
            items.add(item("project", tags));
        }

        List<String> warnings = service.regenerateSplitAssignments(dataset, items);

        assertThat(warnings).isEmpty();
        assertThat(countsBySplit(items)).containsOnly(
                Map.entry(DatasetItem.Split.TRAIN, 70L), Map.entry(DatasetItem.Split.VAL, 15L), Map.entry(DatasetItem.Split.TEST, 15L));
        Map<DatasetItem.Split, Long> print = countsBySplit(items.stream().filter(item -> item.getSourcePageTags().contains("print")).toList());
        assertThat(print.get(DatasetItem.Split.TRAIN)).isBetween(27L, 29L);
        assertThat(print.get(DatasetItem.Split.VAL)).isBetween(5L, 7L);
        assertThat(print.get(DatasetItem.Split.TEST)).isBetween(5L, 7L);
        Map<DatasetItem.Split, Long> marginalia = countsBySplit(items.stream().filter(item -> item.getSourcePageTags().contains("marginalia")).toList());
        assertThat(marginalia.get(DatasetItem.Split.TRAIN)).isBetween(13L, 15L);
        assertThat(marginalia.get(DatasetItem.Split.VAL)).isBetween(2L, 4L);
        assertThat(marginalia.get(DatasetItem.Split.TEST)).isBetween(2L, 4L);
    }

    @Test
    void multilabelStratifiedSendsARareTagToEverySplit() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_VAL_TEST, Dataset.SplitAlgorithm.MULTILABEL_STRATIFIED_BY_TAGS);
        dataset.setStratifyTagIds(List.of("rubric"));
        List<DatasetItem> items = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            items.add(item("project", index < 3 ? List.of("rubric") : List.of()));
        }

        service.regenerateSplitAssignments(dataset, items);

        assertThat(items.subList(0, 3)).extracting(DatasetItem::getAssignedSplit)
                .containsExactlyInAnyOrder(DatasetItem.Split.TRAIN, DatasetItem.Split.VAL, DatasetItem.Split.TEST);
        assertThat(countsBySplit(items)).containsOnly(
                Map.entry(DatasetItem.Split.TRAIN, 14L), Map.entry(DatasetItem.Split.VAL, 3L), Map.entry(DatasetItem.Split.TEST, 3L));
    }

    @Test
    void multilabelStratifiedUsesTheGivenItemTags() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_VAL_TEST, Dataset.SplitAlgorithm.MULTILABEL_STRATIFIED_BY_TAGS);
        dataset.setStratifyTagIds(List.of("parent"));
        List<DatasetItem> items = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            items.add(item("project", index < 3 ? List.of("child") : List.of()));
        }

        List<String> warnings = service.regenerateSplitAssignments(dataset, items, item -> item.getSourcePageTags().contains("child")
                ? java.util.Set.of("child", "parent")
                : java.util.Set.of());

        assertThat(warnings).isEmpty();
        assertThat(items.subList(0, 3)).extracting(DatasetItem::getAssignedSplit)
                .containsExactlyInAnyOrder(DatasetItem.Split.TRAIN, DatasetItem.Split.VAL, DatasetItem.Split.TEST);
    }

    @Test
    void multilabelStratifiedIsReproducibleForTheSameSeed() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_VAL_TEST, Dataset.SplitAlgorithm.MULTILABEL_STRATIFIED_BY_TAGS);
        dataset.setStratifyTagIds(List.of("even"));
        List<DatasetItem> first = items(30);
        List<DatasetItem> second = items(30);

        service.regenerateSplitAssignments(dataset, first);
        service.regenerateSplitAssignments(dataset, second);

        assertThat(first).extracting(DatasetItem::getAssignedSplit)
                .containsExactlyElementsOf(second.stream().map(DatasetItem::getAssignedSplit).toList());
    }

    @Test
    void addingItemsKeepsExistingLinkedAssignments() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_VAL_TEST, Dataset.SplitAlgorithm.RANDOM_SEEDED);
        List<DatasetItem> existing = items(20);
        service.regenerateSplitAssignments(dataset, existing);
        List<DatasetItem.Split> before = existing.stream().map(DatasetItem::getAssignedSplit).toList();

        service.assignSplitsToNewItems(dataset, existing, items(5));

        assertThat(existing).extracting(DatasetItem::getAssignedSplit).containsExactlyElementsOf(before);
    }

    @Test
    void addingItemsFillsSplitsTowardTheConfiguredTargets() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_VAL_TEST, Dataset.SplitAlgorithm.RANDOM_SEEDED);
        List<DatasetItem> existing = items(10);
        service.regenerateSplitAssignments(dataset, existing);
        List<DatasetItem> added = items(10);

        service.assignSplitsToNewItems(dataset, existing, added);

        List<DatasetItem> all = new ArrayList<>(existing);
        all.addAll(added);
        assertThat(countsBySplit(all)).containsEntry(DatasetItem.Split.TRAIN, 14L);
        assertThat(countsBySplit(all)).containsEntry(DatasetItem.Split.VAL, 3L);
        assertThat(countsBySplit(all)).containsEntry(DatasetItem.Split.TEST, 3L);
    }

    @Test
    void addingItemsKeepsManualAssignments() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_VAL_TEST, Dataset.SplitAlgorithm.RANDOM_SEEDED);
        List<DatasetItem> existing = items(4);
        existing.forEach(item -> {
            item.setAssignedSplit(DatasetItem.Split.TEST);
            item.setManualSplit(true);
        });

        service.assignSplitsToNewItems(dataset, existing, items(1));

        assertThat(existing).allSatisfy(item -> assertThat(item.getAssignedSplit()).isEqualTo(DatasetItem.Split.TEST));
    }

    @Test
    void addingItemsToAnEmptyDatasetMatchesRegeneration() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_VAL_TEST, Dataset.SplitAlgorithm.RANDOM_SEEDED);
        List<DatasetItem> regenerated = items(10);
        List<DatasetItem> added = items(10);

        service.regenerateSplitAssignments(dataset, regenerated);
        service.assignSplitsToNewItems(dataset, List.of(), added);

        assertThat(added).extracting(DatasetItem::getAssignedSplit)
                .containsExactlyElementsOf(regenerated.stream().map(DatasetItem::getAssignedSplit).toList());
    }

    @Test
    void newPagesJoinTheirProjectSplitWhenGroupingByProject() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_VAL_TEST, Dataset.SplitAlgorithm.GROUP_BY_SOURCE_PROJECT);
        List<DatasetItem> existing = items(2);
        existing.get(0).setSourceProjectId("held-out");
        existing.get(0).setAssignedSplit(DatasetItem.Split.TEST);
        existing.get(1).setSourceProjectId("trained-on");
        existing.get(1).setAssignedSplit(DatasetItem.Split.TRAIN);
        List<DatasetItem> added = items(2);
        added.forEach(item -> item.setSourceProjectId("held-out"));

        service.assignSplitsToNewItems(dataset, existing, added);

        assertThat(added).allSatisfy(item -> assertThat(item.getAssignedSplit()).isEqualTo(DatasetItem.Split.TEST));
    }

    @Test
    void stableSplitIsRepeatableAndCloseToTheManagedPercentages() {
        Map<DatasetItem.Split, Long> counts = java.util.stream.IntStream.range(0, 10_000)
                .mapToObj(index -> service.stableSplit("page-" + index))
                .collect(Collectors.groupingBy(split -> split, Collectors.counting()));

        assertThat(service.stableSplit("page-1")).isEqualTo(service.stableSplit("page-1"));
        assertThat(counts.get(DatasetItem.Split.TRAIN)).isBetween(6_800L, 7_200L);
        assertThat(counts.get(DatasetItem.Split.VAL)).isBetween(1_350L, 1_650L);
        assertThat(counts.get(DatasetItem.Split.TEST)).isBetween(1_350L, 1_650L);
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

    @ParameterizedTest
    @EnumSource(Dataset.SplitTemplate.class)
    void everyAlgorithmOnlyAssignsTheSplitsOfTheTemplate(Dataset.SplitTemplate template) {
        for (Dataset.SplitAlgorithm algorithm : Dataset.SplitAlgorithm.values()) {
            List<DatasetItem> items = items(20);
            Dataset dataset = dataset(template, algorithm);
            dataset.setTrainPercentage(service.defaultPercentage(template, DatasetItem.Split.TRAIN));
            dataset.setValPercentage(service.defaultPercentage(template, DatasetItem.Split.VAL));
            dataset.setTestPercentage(service.defaultPercentage(template, DatasetItem.Split.TEST));
            dataset.setStratifyTagIds(List.of("tag"));

            service.regenerateSplitAssignments(dataset, items);

            assertThat(items).extracting(DatasetItem::getAssignedSplit).isSubsetOf(template.splits());
        }
    }

    @Test
    void randomAssignmentFollowsThePercentagesOfTwoSplits() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TRAIN_TEST, Dataset.SplitAlgorithm.RANDOM_SEEDED);
        dataset.setTrainPercentage(60);
        dataset.setValPercentage(0);
        dataset.setTestPercentage(40);
        List<DatasetItem> items = items(10);

        service.regenerateSplitAssignments(dataset, items);

        assertThat(countsBySplit(items)).containsOnly(Map.entry(DatasetItem.Split.TRAIN, 6L), Map.entry(DatasetItem.Split.TEST, 4L));
    }

    @Test
    void aSingleSplitAlwaysTakesTheWholeDataset() {
        Dataset dataset = dataset(Dataset.SplitTemplate.TEST, Dataset.SplitAlgorithm.RANDOM_SEEDED);
        dataset.setTrainPercentage(70);
        dataset.setValPercentage(15);
        dataset.setTestPercentage(15);

        service.validateSplitConfiguration(dataset);

        assertThat(dataset.getTrainPercentage()).isZero();
        assertThat(dataset.getValPercentage()).isZero();
        assertThat(dataset.getTestPercentage()).isEqualTo(100);
    }

    @Test
    void percentagesOfUnusedSplitsDoNotCount() {
        Dataset dataset = dataset(Dataset.SplitTemplate.VAL_TEST, Dataset.SplitAlgorithm.RANDOM_SEEDED);
        dataset.setTrainPercentage(99);
        dataset.setValPercentage(30);
        dataset.setTestPercentage(70);

        service.validateSplitConfiguration(dataset);

        assertThat(dataset.getTrainPercentage()).isZero();
        dataset.setValPercentage(40);
        assertThatThrownBy(() -> service.validateSplitConfiguration(dataset))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("add up to 100");
    }

    @Test
    void unusedSplitsMoveAwayFromTrainingWherePossible() {
        assertThat(service.normalizeSplitForTemplate(Dataset.SplitTemplate.TRAIN_TEST, DatasetItem.Split.VAL)).isEqualTo(DatasetItem.Split.TEST);
        assertThat(service.normalizeSplitForTemplate(Dataset.SplitTemplate.VAL_TEST, DatasetItem.Split.TRAIN)).isEqualTo(DatasetItem.Split.VAL);
        assertThat(service.normalizeSplitForTemplate(Dataset.SplitTemplate.TEST, DatasetItem.Split.TRAIN)).isEqualTo(DatasetItem.Split.TEST);
        assertThat(service.normalizeSplitForTemplate(Dataset.SplitTemplate.TRAIN, DatasetItem.Split.TEST)).isEqualTo(DatasetItem.Split.TRAIN);
        assertThat(service.normalizeSplitForTemplate(Dataset.SplitTemplate.TEST, null)).isEqualTo(DatasetItem.Split.TEST);
    }

    @ParameterizedTest
    @EnumSource(Dataset.SplitTemplate.class)
    void defaultPercentagesAddUpTo100(Dataset.SplitTemplate template) {
        assertThat(java.util.Arrays.stream(DatasetItem.Split.values()).mapToInt(split -> service.defaultPercentage(template, split)).sum())
                .isEqualTo(100);
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

    private DatasetItem item(String projectId, List<String> tags) {
        DatasetItem item = new DatasetItem();
        item.setSourceProjectId(projectId);
        item.setSourcePageTags(tags);
        item.setMode(DatasetItem.Mode.LINK);
        return item;
    }

    private Map<DatasetItem.Split, Long> countsBySplit(List<DatasetItem> items) {
        return items.stream().collect(Collectors.groupingBy(DatasetItem::getAssignedSplit, Collectors.counting()));
    }
}
