package de.uniwue.zpd.dachs.larex.backend.dto.page;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * DTO for reading order, aligned with page4j's ReadingOrder.
 * PAGE XML ReadingOrder element.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReadingOrderDto(
    /** Root group */
    GroupDto root,
    /** Confidence score */
    Double confidence
) {
    /**
     * DTO for a reading order group, aligned with page4j's Group.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record GroupDto(
        /** Group ID */
        String id,
        /** Whether this is an ordered group */
        boolean ordered,
        /** Caption/display name */
        String caption,
        /** Reference to a parent region (for nested region groups) */
        String regionRef,
        /** Group members (can be RegionRefs or nested Groups) */
        List<GroupMemberDto> members,
        /** Custom attribute */
        String custom,
        /** Comments */
        String comments
    ) {}

    /**
     * DTO for a group member - either a region reference or a nested group.
     */
    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(value = RegionRefDto.class, name = "regionRef"),
        @JsonSubTypes.Type(value = NestedGroupDto.class, name = "nestedGroup")
    })
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public sealed interface GroupMemberDto permits RegionRefDto, NestedGroupDto {}

    /**
     * DTO for a region reference in the reading order.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RegionRefDto(
        /** Reference ID */
        String id,
        /** Referenced region ID */
        String regionRef,
        /** Index (for indexed groups) */
        Integer index
    ) implements GroupMemberDto {}

    /**
     * DTO for a nested group in the reading order.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NestedGroupDto(
        GroupDto group
    ) implements GroupMemberDto {}
}
