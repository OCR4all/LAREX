package de.uniwue.zpd.dachs.larex.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class PageMoveDto {
    private PageMoveDto() {}

    public enum ConflictPolicy { SKIP, OVERWRITE, RENAME }
    public enum Outcome { MOVE, SKIP, OVERWRITE, RENAME }

    public record Request(
            @NotEmpty List<@NotBlank String> pageIds,
            @NotBlank String destinationProjectId,
            @NotNull ConflictPolicy conflictPolicy,
            @Size(max = 255) String prefix,
            @Size(max = 255) String suffix,
            String fingerprint
    ) {}

    public record Item(String pageId, String sourceName, String resultingName,
                       Outcome outcome, String overwrittenPageId, List<String> blockers) {}

    public record Preview(List<Item> items, List<String> blockers, int movedCount,
                          int skippedCount, int overwrittenCount, int renamedCount,
                          String fingerprint) {
        public boolean canMove() {
            return movedCount > 0 && blockers.isEmpty()
                    && items.stream().allMatch(item -> item.blockers().isEmpty());
        }
    }
}
