package de.uniwue.zpd.dachs.larex.backend.dto.action;

import jakarta.validation.Valid;
import de.uniwue.zpd.dachs.larex.backend.entity.ImageType;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ActionWorkflowDto {
    private ActionWorkflowDto() {}

    public record Step(@NotBlank String processorDefinitionId, Map<String, Object> parameters,
                       ActionDto.ImageVariantSelection imageVariantSelection, List<ImageType> imageTypes) {
        public Step(String processorDefinitionId, Map<String, Object> parameters, ActionDto.ImageVariantSelection imageVariantSelection) {
            this(processorDefinitionId, parameters, imageVariantSelection, null);
        }
    }

    public record SaveRequest(@NotBlank @Size(max = 120) String name,
                              @Size(max = 4000) String description, boolean enabled,
                              @NotNull @Size(min = 1, max = 30) List<@NotNull @Valid Step> steps,
                              Long version) {}

    public record Definition(String id, String name, String description, boolean enabled,
                             List<Step> steps, Long version, String processorDefinitionId, boolean global) {}

    // Overrides are positional: repeated occurrences of a processor have independent parameters.
    public record StartRequest(@NotBlank String workflowId, @NotNull Long version,
                               List<String> pageIds, @Size(min = 1, max = 30) List<@NotNull @Valid Step> steps) {}

    public record Run(String id, String workflowId, String workflowName, String status,
                      List<Step> steps, List<String> pageIds, List<String> stepRunIds,
                      String errorMessage, boolean canCancel, LocalDateTime created, LocalDateTime completedAt) {}
}
