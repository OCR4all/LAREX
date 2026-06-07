package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import de.uniwue.zpd.dachs.larex.backend.entity.Dataset;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DatasetSplitService {

    public List<String> regenerateSplitAssignments(Dataset dataset, List<DatasetItem> items) {
        validateSplitConfiguration(dataset);
        List<String> warnings = new ArrayList<>();

        Dataset.SplitTemplate template = dataset.getSplitTemplate();
        Map<DatasetItem.Split, Integer> targetCounts = targetCounts(template, dataset.getTrainPercentage(),
                dataset.getValPercentage(), dataset.getTestPercentage(), items.size());

        List<DatasetItem> preservedItems = items.stream()
                .filter(this::shouldPreserveSplit)
                .toList();

        Map<DatasetItem.Split, Integer> preservedCounts = new EnumMap<>(DatasetItem.Split.class);
        for (DatasetItem.Split split : DatasetItem.Split.values()) {
            preservedCounts.put(split, 0);
        }
        for (DatasetItem item : preservedItems) {
            DatasetItem.Split split = normalizeSplitForTemplate(template, item.getAssignedSplit());
            item.setAssignedSplit(split);
            preservedCounts.put(split, preservedCounts.get(split) + 1);
        }

        for (Map.Entry<DatasetItem.Split, Integer> entry : preservedCounts.entrySet()) {
            if (entry.getValue() > targetCounts.getOrDefault(entry.getKey(), 0)) {
                warnings.add("Preserved assignments exceed target size for split " + entry.getKey().name().toLowerCase(Locale.ROOT) + ".");
            }
        }

        List<DatasetItem> mutableItems = items.stream()
                .filter(item -> !preservedItems.contains(item))
                .toList();
        Map<DatasetItem.Split, Integer> remainingCounts = new EnumMap<>(DatasetItem.Split.class);
        for (DatasetItem.Split split : allowedSplits(template)) {
            remainingCounts.put(split, Math.max(0, targetCounts.getOrDefault(split, 0) - preservedCounts.getOrDefault(split, 0)));
        }

        switch (dataset.getSplitAlgorithm()) {
            case RANDOM_SEEDED -> assignRandomly(mutableItems, remainingCounts, template, dataset.getSplitSeed());
            case GROUP_BY_SOURCE_PROJECT -> assignGrouped(
                    mutableItems,
                    remainingCounts,
                    template,
                    dataset.getSplitSeed(),
                    DatasetItem::getSourceProjectId
            );
            case MULTILABEL_STRATIFIED_BY_TAGS -> assignMultilabelStratified(mutableItems, remainingCounts, template, dataset.getSplitSeed(),
                    dataset.getStratifyTagIds(), warnings);
        }

        for (DatasetItem item : items) {
            item.setAssignedSplit(normalizeSplitForTemplate(template, item.getAssignedSplit()));
        }

        return warnings;
    }

    public void validateSplitConfiguration(Dataset dataset) {
        int train = defaultInt(dataset.getTrainPercentage());
        int val = defaultInt(dataset.getValPercentage());
        int test = dataset.getSplitTemplate() == Dataset.SplitTemplate.TRAIN_VAL ? 0 : defaultInt(dataset.getTestPercentage());

        if (train < 0 || val < 0 || test < 0) {
            throw new IllegalArgumentException("Split percentages must be non-negative");
        }
        int total = train + val + test;
        if (total != 100) {
            throw new IllegalArgumentException("Split percentages must add up to 100");
        }
        if (dataset.getSplitTemplate() == Dataset.SplitTemplate.TRAIN_VAL) {
            dataset.setTestPercentage(0);
        }
    }

    public DatasetItem.Split normalizeSplitForTemplate(Dataset.SplitTemplate template, DatasetItem.Split split) {
        if (split == null) {
            return DatasetItem.Split.TRAIN;
        }
        if (template == Dataset.SplitTemplate.TRAIN_VAL && split == DatasetItem.Split.TEST) {
            return DatasetItem.Split.VAL;
        }
        return split;
    }

    private boolean shouldPreserveSplit(DatasetItem item) {
        return item.getMode() == DatasetItem.Mode.COPY && item.getCopiedAt() != null;
    }

    private void assignRandomly(List<DatasetItem> items,
                                Map<DatasetItem.Split, Integer> remainingCounts,
                                Dataset.SplitTemplate template,
                                Long seed) {
        List<DatasetItem> shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled, new Random(seed == null ? 42L : seed));
        List<DatasetItem.Split> splitOrder = allowedSplits(template);
        int cursor = 0;
        for (DatasetItem.Split split : splitOrder) {
            int amount = remainingCounts.getOrDefault(split, 0);
            for (int i = 0; i < amount && cursor < shuffled.size(); i++) {
                shuffled.get(cursor++).setAssignedSplit(split);
            }
        }
        while (cursor < shuffled.size()) {
            shuffled.get(cursor++).setAssignedSplit(fallbackSplit(template));
        }
    }

    private void assignGrouped(List<DatasetItem> items,
                               Map<DatasetItem.Split, Integer> remainingCounts,
                               Dataset.SplitTemplate template,
                               Long seed,
                               java.util.function.Function<DatasetItem, String> grouper) {
        Map<String, List<DatasetItem>> groups = items.stream()
                .collect(Collectors.groupingBy(grouper, LinkedHashMap::new, Collectors.toList()));
        List<Map.Entry<String, List<DatasetItem>>> entries = new ArrayList<>(groups.entrySet());
        Collections.shuffle(entries, new Random(seed == null ? 42L : seed));
        Map<DatasetItem.Split, Integer> assignedCounts = new EnumMap<>(DatasetItem.Split.class);
        for (DatasetItem.Split split : allowedSplits(template)) {
            assignedCounts.put(split, 0);
        }

        for (Map.Entry<String, List<DatasetItem>> entry : entries) {
            DatasetItem.Split bestSplit = bestSplitForGroup(entry.getValue().size(), remainingCounts, assignedCounts, template);
            for (DatasetItem item : entry.getValue()) {
                item.setAssignedSplit(bestSplit);
            }
            assignedCounts.put(bestSplit, assignedCounts.get(bestSplit) + entry.getValue().size());
        }
    }

    private void assignMultilabelStratified(List<DatasetItem> items,
                                            Map<DatasetItem.Split, Integer> remainingCounts,
                                            Dataset.SplitTemplate template,
                                            Long seed,
                                            List<String> stratifyTagIds,
                                            List<String> warnings) {
        List<String> effectiveTags = stratifyTagIds == null ? List.of() : stratifyTagIds.stream()
                .filter(Objects::nonNull)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .toList();
        if (effectiveTags.isEmpty()) {
            warnings.add("Multilabel stratified splitting requested without stratify tags. Falling back to random seeded assignment.");
            assignRandomly(items, remainingCounts, template, seed);
            return;
        }

        Map<String, List<DatasetItem>> buckets = items.stream()
                .collect(Collectors.groupingBy(item -> stratifySignature(item, effectiveTags), LinkedHashMap::new, Collectors.toList()));
        Random random = new Random(seed == null ? 42L : seed);

        for (List<DatasetItem> bucketItems : buckets.values()) {
            List<DatasetItem> shuffledBucket = new ArrayList<>(bucketItems);
            Collections.shuffle(shuffledBucket, random);
            assignRandomly(shuffledBucket, deriveBucketTargets(shuffledBucket.size(), remainingCounts, template), template, random.nextLong());
        }
    }

    private Map<DatasetItem.Split, Integer> deriveBucketTargets(int bucketSize,
                                                                Map<DatasetItem.Split, Integer> remainingCounts,
                                                                Dataset.SplitTemplate template) {
        int totalRemaining = remainingCounts.values().stream().mapToInt(Integer::intValue).sum();
        if (totalRemaining <= 0) {
            return Map.of(fallbackSplit(template), bucketSize);
        }
        Map<DatasetItem.Split, Integer> targets = new EnumMap<>(DatasetItem.Split.class);
        int assigned = 0;
        List<DatasetItem.Split> splits = allowedSplits(template);
        for (int i = 0; i < splits.size(); i++) {
            DatasetItem.Split split = splits.get(i);
            if (i == splits.size() - 1) {
                targets.put(split, Math.max(0, bucketSize - assigned));
                continue;
            }
            double fraction = remainingCounts.getOrDefault(split, 0) / (double) totalRemaining;
            int count = (int) Math.round(bucketSize * fraction);
            targets.put(split, count);
            assigned += count;
        }
        return targets;
    }

    private DatasetItem.Split bestSplitForGroup(int groupSize,
                                                Map<DatasetItem.Split, Integer> remainingCounts,
                                                Map<DatasetItem.Split, Integer> assignedCounts,
                                                Dataset.SplitTemplate template) {
        return allowedSplits(template).stream()
                .min(Comparator.comparingInt(split -> {
                    int remaining = remainingCounts.getOrDefault(split, 0) - assignedCounts.getOrDefault(split, 0);
                    if (remaining >= groupSize) {
                        return remaining - groupSize;
                    }
                    return Math.abs(remaining) + groupSize;
                }))
                .orElse(fallbackSplit(template));
    }

    private Map<DatasetItem.Split, Integer> targetCounts(Dataset.SplitTemplate template,
                                                         int trainPct,
                                                         int valPct,
                                                         int testPct,
                                                         int itemCount) {
        Map<DatasetItem.Split, Integer> counts = new EnumMap<>(DatasetItem.Split.class);
        int train = (int) Math.round(itemCount * (trainPct / 100.0));
        int val = (int) Math.round(itemCount * (valPct / 100.0));
        int assigned = train + val;
        int test = Math.max(0, itemCount - assigned);
        counts.put(DatasetItem.Split.TRAIN, train);
        counts.put(DatasetItem.Split.VAL, val);
        if (template == Dataset.SplitTemplate.TRAIN_VAL_TEST) {
            counts.put(DatasetItem.Split.TEST, test);
        } else {
            counts.put(DatasetItem.Split.TEST, 0);
            if (assigned < itemCount) {
                counts.put(DatasetItem.Split.VAL, counts.get(DatasetItem.Split.VAL) + (itemCount - assigned));
            }
        }
        return counts;
    }

    private List<DatasetItem.Split> allowedSplits(Dataset.SplitTemplate template) {
        if (template == Dataset.SplitTemplate.TRAIN_VAL) {
            return List.of(DatasetItem.Split.TRAIN, DatasetItem.Split.VAL);
        }
        return List.of(DatasetItem.Split.TRAIN, DatasetItem.Split.VAL, DatasetItem.Split.TEST);
    }

    private DatasetItem.Split fallbackSplit(Dataset.SplitTemplate template) {
        return template == Dataset.SplitTemplate.TRAIN_VAL ? DatasetItem.Split.VAL : DatasetItem.Split.TEST;
    }

    private String stratifySignature(DatasetItem item, List<String> stratifyTagIds) {
        Set<String> tags = new LinkedHashSet<>(defaultList(item.getSourcePageTags()));
        List<String> matching = stratifyTagIds.stream()
                .filter(tags::contains)
                .sorted()
                .toList();
        return matching.isEmpty() ? "__UNTAGGED__" : String.join("|", matching);
    }

    private List<String> defaultList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
