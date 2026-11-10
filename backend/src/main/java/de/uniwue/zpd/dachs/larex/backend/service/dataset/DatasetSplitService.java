package de.uniwue.zpd.dachs.larex.backend.service.dataset;

import de.uniwue.zpd.dachs.larex.backend.entity.Dataset;
import de.uniwue.zpd.dachs.larex.backend.entity.DatasetItem;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DatasetSplitService {

    public static final int MANAGED_TRAIN_PERCENTAGE = 70;
    public static final int MANAGED_VAL_PERCENTAGE = 15;
    public static final int MANAGED_TEST_PERCENTAGE = 15;

    public List<String> regenerateSplitAssignments(Dataset dataset, List<DatasetItem> items) {
        return regenerateSplitAssignments(dataset, items, DatasetSplitService::directPageTags);
    }

    /**
     * @param itemTags the tag IDs an item counts as carrying for tag stratification, e.g. including ancestors of its page tags
     */
    public List<String> regenerateSplitAssignments(Dataset dataset, List<DatasetItem> items, Function<DatasetItem, Set<String>> itemTags) {
        List<DatasetItem> preservedItems = items.stream().filter(this::shouldPreserveSplit).toList();
        List<DatasetItem> mutableItems = items.stream().filter(item -> !shouldPreserveSplit(item)).toList();
        return assignSplits(dataset, preservedItems, mutableItems, itemTags);
    }

    /**
     * Assigns splits to {@code newItems} only, so pages already held out never move into training.
     * With project grouping, a new page joins the split its source project already uses.
     */
    public List<String> assignSplitsToNewItems(Dataset dataset, List<DatasetItem> existingItems, List<DatasetItem> newItems) {
        return assignSplitsToNewItems(dataset, existingItems, newItems, DatasetSplitService::directPageTags);
    }

    public List<String> assignSplitsToNewItems(Dataset dataset,
                                               List<DatasetItem> existingItems,
                                               List<DatasetItem> newItems,
                                               Function<DatasetItem, Set<String>> itemTags) {
        Map<String, DatasetItem.Split> projectSplits = dataset.getSplitAlgorithm() == Dataset.SplitAlgorithm.GROUP_BY_SOURCE_PROJECT
                ? dominantSplitByProject(existingItems)
                : Map.of();
        List<DatasetItem> preservedItems = new ArrayList<>(existingItems);
        List<DatasetItem> unassignedItems = new ArrayList<>();
        for (DatasetItem item : newItems) {
            DatasetItem.Split projectSplit = projectSplits.get(item.getSourceProjectId());
            if (projectSplit == null) {
                unassignedItems.add(item);
            } else {
                item.setAssignedSplit(projectSplit);
                preservedItems.add(item);
            }
        }
        return assignSplits(dataset, preservedItems, unassignedItems, itemTags);
    }

    /**
     * Derives a split from the page ID alone, so a page keeps its split as projects grow
     * and in every combination of projects it is part of.
     */
    public DatasetItem.Split stableSplit(String pageId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(pageId.getBytes(StandardCharsets.UTF_8));
            int bucket = Math.floorMod(ByteBuffer.wrap(digest).getInt(), 100);
            if (bucket < MANAGED_TRAIN_PERCENTAGE) return DatasetItem.Split.TRAIN;
            return bucket < MANAGED_TRAIN_PERCENTAGE + MANAGED_VAL_PERCENTAGE ? DatasetItem.Split.VAL : DatasetItem.Split.TEST;
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private List<String> assignSplits(Dataset dataset,
                                      List<DatasetItem> preservedItems,
                                      List<DatasetItem> mutableItems,
                                      Function<DatasetItem, Set<String>> itemTags) {
        validateSplitConfiguration(dataset);
        List<String> warnings = new ArrayList<>();

        Dataset.SplitTemplate template = dataset.getSplitTemplate();
        Map<DatasetItem.Split, Integer> targetCounts = targetCounts(dataset, preservedItems.size() + mutableItems.size());

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

        Map<DatasetItem.Split, Integer> remainingCounts = new EnumMap<>(DatasetItem.Split.class);
        for (DatasetItem.Split split : template.splits()) {
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
            case MULTILABEL_STRATIFIED_BY_TAGS -> assignMultilabelStratified(preservedItems, mutableItems, targetCounts, remainingCounts,
                    template, dataset.getSplitSeed(), dataset.getStratifyTagIds(), itemTags, warnings);
        }

        for (DatasetItem item : mutableItems) {
            item.setAssignedSplit(normalizeSplitForTemplate(template, item.getAssignedSplit()));
        }

        return warnings;
    }

    public void validateSplitConfiguration(Dataset dataset) {
        List<DatasetItem.Split> splits = dataset.getSplitTemplate().splits();
        if (splits.size() == 1) {
            setPercentage(dataset, splits.getFirst(), 100);
        }
        int total = 0;
        for (DatasetItem.Split split : DatasetItem.Split.values()) {
            if (!splits.contains(split)) {
                setPercentage(dataset, split, 0);
                continue;
            }
            int percentage = percentage(dataset, split);
            if (percentage < 0) {
                throw new IllegalArgumentException("Split percentages must be non-negative");
            }
            total += percentage;
        }
        if (total != 100) {
            throw new IllegalArgumentException("Split percentages must add up to 100");
        }
    }

    public int defaultPercentage(Dataset.SplitTemplate template, DatasetItem.Split split) {
        List<DatasetItem.Split> splits = template.splits();
        if (!splits.contains(split)) return 0;
        if (splits.size() == 1) return 100;
        if (!splits.contains(DatasetItem.Split.TRAIN)) return 50;
        if (split == DatasetItem.Split.TRAIN) return splits.size() == 3 ? 70 : 80;
        return splits.size() == 3 ? 15 : 20;
    }

    /** Moves a split the template doesn't use away from training where possible, so held-out pages stay held out. */
    public DatasetItem.Split normalizeSplitForTemplate(Dataset.SplitTemplate template, DatasetItem.Split split) {
        List<DatasetItem.Split> splits = template.splits();
        if (split == null) {
            return splits.getFirst();
        }
        if (splits.contains(split)) {
            return split;
        }
        return splits.stream()
                .filter(candidate -> candidate.compareTo(split) > 0)
                .findFirst()
                .orElse(splits.getLast());
    }

    private boolean shouldPreserveSplit(DatasetItem item) {
        return item.getMode() == DatasetItem.Mode.COPY && item.getCopiedAt() != null;
    }

    private Map<String, DatasetItem.Split> dominantSplitByProject(List<DatasetItem> items) {
        return items.stream().collect(Collectors.groupingBy(DatasetItem::getSourceProjectId, Collectors.collectingAndThen(
                Collectors.groupingBy(DatasetItem::getAssignedSplit, () -> new EnumMap<>(DatasetItem.Split.class), Collectors.counting()),
                counts -> Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey())));
    }

    private void assignRandomly(List<DatasetItem> items,
                                Map<DatasetItem.Split, Integer> remainingCounts,
                                Dataset.SplitTemplate template,
                                Long seed) {
        List<DatasetItem> shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled, new Random(seed == null ? 42L : seed));
        List<DatasetItem.Split> splitOrder = template.splits();
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
        for (DatasetItem.Split split : template.splits()) {
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

    /**
     * Iterative stratification (Sechidis et al., 2011): balances each selected tag across the splits on its own,
     * handling the rarest tag first, instead of bucketing pages by their exact tag combination.
     * Pages without a selected tag then fill the remaining split sizes.
     */
    private void assignMultilabelStratified(List<DatasetItem> preservedItems,
                                            List<DatasetItem> items,
                                            Map<DatasetItem.Split, Integer> targetCounts,
                                            Map<DatasetItem.Split, Integer> remainingCounts,
                                            Dataset.SplitTemplate template,
                                            Long seed,
                                            List<String> stratifyTagIds,
                                            Function<DatasetItem, Set<String>> itemTags,
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

        Set<String> selected = new LinkedHashSet<>(effectiveTags);
        Function<DatasetItem, Set<String>> labelsOf = item -> {
            Set<String> labels = new LinkedHashSet<>(itemTags.apply(item));
            labels.retainAll(selected);
            return labels;
        };
        Map<DatasetItem, Set<String>> itemLabels = new LinkedHashMap<>();
        for (DatasetItem item : items) {
            itemLabels.put(item, labelsOf.apply(item));
        }
        Map<DatasetItem, Set<String>> preservedLabels = new LinkedHashMap<>();
        for (DatasetItem item : preservedItems) {
            preservedLabels.put(item, labelsOf.apply(item));
        }

        boolean anyTagged = itemLabels.values().stream().anyMatch(labels -> !labels.isEmpty())
                || preservedLabels.values().stream().anyMatch(labels -> !labels.isEmpty());
        if (!anyTagged) {
            warnings.add("No page in this dataset carries any of the selected stratify tags. Falling back to random seeded assignment.");
            assignRandomly(items, remainingCounts, template, seed);
            return;
        }

        List<DatasetItem.Split> splits = template.splits();
        int totalTarget = targetCounts.values().stream().mapToInt(Integer::intValue).sum();
        Map<DatasetItem.Split, Double> shares = new EnumMap<>(DatasetItem.Split.class);
        for (DatasetItem.Split split : splits) {
            shares.put(split, totalTarget == 0 ? 0.0 : targetCounts.getOrDefault(split, 0) / (double) totalTarget);
        }
        List<DatasetItem.Split> usedSplits = splits.stream().filter(split -> shares.get(split) > 0).toList();

        Map<String, Map<DatasetItem.Split, Integer>> labelCounts = new HashMap<>();
        Map<String, Integer> labelTotals = new HashMap<>();
        Map<String, Integer> labelRemaining = new HashMap<>();
        for (String label : effectiveTags) {
            Map<DatasetItem.Split, Integer> counts = new EnumMap<>(DatasetItem.Split.class);
            splits.forEach(split -> counts.put(split, 0));
            labelCounts.put(label, counts);
            labelTotals.put(label, 0);
            labelRemaining.put(label, 0);
        }
        preservedLabels.forEach((item, labels) -> labels.forEach(label -> {
            labelCounts.get(label).merge(item.getAssignedSplit(), 1, Integer::sum);
            labelTotals.merge(label, 1, Integer::sum);
        }));
        itemLabels.values().forEach(labels -> labels.forEach(label -> {
            labelTotals.merge(label, 1, Integer::sum);
            labelRemaining.merge(label, 1, Integer::sum);
        }));

        Map<DatasetItem.Split, Double> splitDesire = new EnumMap<>(DatasetItem.Split.class);
        splits.forEach(split -> splitDesire.put(split, (double) remainingCounts.getOrDefault(split, 0)));

        Random random = new Random(seed == null ? 42L : seed);
        List<DatasetItem> shuffled = new ArrayList<>(items);
        Collections.shuffle(shuffled, random);
        Set<DatasetItem> unassigned = new LinkedHashSet<>(shuffled);

        while (true) {
            String label = effectiveTags.stream()
                    .filter(candidate -> labelRemaining.get(candidate) > 0)
                    .min(Comparator.comparingInt(labelRemaining::get))
                    .orElse(null);
            if (label == null) break;

            List<DatasetItem> labelItems = unassigned.stream().filter(item -> itemLabels.get(item).contains(label)).toList();
            for (DatasetItem item : labelItems) {
                DatasetItem.Split split = chooseSplitForLabel(label, splits, usedSplits, shares, labelCounts, labelTotals,
                        labelRemaining, splitDesire, random);
                item.setAssignedSplit(split);
                unassigned.remove(item);
                splitDesire.merge(split, -1.0, Double::sum);
                for (String itemLabel : itemLabels.get(item)) {
                    labelCounts.get(itemLabel).merge(split, 1, Integer::sum);
                    labelRemaining.merge(itemLabel, -1, Integer::sum);
                }
            }
        }

        for (DatasetItem item : unassigned) {
            DatasetItem.Split split = splits.stream()
                    .filter(candidate -> splitDesire.get(candidate) > 0)
                    .max(Comparator.comparingDouble(splitDesire::get))
                    .orElse(fallbackSplit(template));
            item.setAssignedSplit(split);
            splitDesire.merge(split, -1.0, Double::sum);
        }
    }

    /**
     * Picks the split furthest below its share of the label. While a label has only as many pages left as there are
     * used splits still missing it, those splits go first, so rare tags reach validation and test too.
     */
    private DatasetItem.Split chooseSplitForLabel(String label,
                                                  List<DatasetItem.Split> splits,
                                                  List<DatasetItem.Split> usedSplits,
                                                  Map<DatasetItem.Split, Double> shares,
                                                  Map<String, Map<DatasetItem.Split, Integer>> labelCounts,
                                                  Map<String, Integer> labelTotals,
                                                  Map<String, Integer> labelRemaining,
                                                  Map<DatasetItem.Split, Double> splitDesire,
                                                  Random random) {
        Map<DatasetItem.Split, Integer> counts = labelCounts.get(label);
        List<DatasetItem.Split> candidates = usedSplits.isEmpty() ? splits : usedSplits;
        List<DatasetItem.Split> missing = candidates.stream().filter(split -> counts.get(split) == 0).toList();
        if (!missing.isEmpty() && labelTotals.get(label) >= candidates.size() && labelRemaining.get(label) <= missing.size()) {
            candidates = missing;
        }

        Comparator<DatasetItem.Split> byDesire = Comparator
                .comparingDouble((DatasetItem.Split split) -> labelTotals.get(label) * shares.get(split) - counts.get(split))
                .thenComparingDouble(splitDesire::get);
        DatasetItem.Split best = Collections.max(candidates, byDesire);
        List<DatasetItem.Split> tied = candidates.stream().filter(split -> byDesire.compare(split, best) == 0).toList();
        return tied.get(random.nextInt(tied.size()));
    }

    private DatasetItem.Split bestSplitForGroup(int groupSize,
                                                Map<DatasetItem.Split, Integer> remainingCounts,
                                                Map<DatasetItem.Split, Integer> assignedCounts,
                                                Dataset.SplitTemplate template) {
        return template.splits().stream()
                .min(Comparator.comparingInt(split -> {
                    int remaining = remainingCounts.getOrDefault(split, 0) - assignedCounts.getOrDefault(split, 0);
                    if (remaining >= groupSize) {
                        return remaining - groupSize;
                    }
                    return Math.abs(remaining) + groupSize;
                }))
                .orElse(fallbackSplit(template));
    }

    private Map<DatasetItem.Split, Integer> targetCounts(Dataset dataset, int itemCount) {
        List<DatasetItem.Split> splits = dataset.getSplitTemplate().splits();
        Map<DatasetItem.Split, Integer> counts = new EnumMap<>(DatasetItem.Split.class);
        int assigned = 0;
        for (DatasetItem.Split split : splits.subList(0, splits.size() - 1)) {
            int count = (int) Math.round(itemCount * (percentage(dataset, split) / 100.0));
            counts.put(split, count);
            assigned += count;
        }
        counts.put(splits.getLast(), Math.max(0, itemCount - assigned));
        return counts;
    }

    private int percentage(Dataset dataset, DatasetItem.Split split) {
        return defaultInt(switch (split) {
            case TRAIN -> dataset.getTrainPercentage();
            case VAL -> dataset.getValPercentage();
            case TEST -> dataset.getTestPercentage();
        });
    }

    private void setPercentage(Dataset dataset, DatasetItem.Split split, int percentage) {
        switch (split) {
            case TRAIN -> dataset.setTrainPercentage(percentage);
            case VAL -> dataset.setValPercentage(percentage);
            case TEST -> dataset.setTestPercentage(percentage);
        }
    }

    private DatasetItem.Split fallbackSplit(Dataset.SplitTemplate template) {
        return template.splits().getLast();
    }

    private static Set<String> directPageTags(DatasetItem item) {
        return item.getSourcePageTags() == null ? Set.of() : new LinkedHashSet<>(item.getSourcePageTags());
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
