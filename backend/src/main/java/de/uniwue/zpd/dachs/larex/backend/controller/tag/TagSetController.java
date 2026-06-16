package de.uniwue.zpd.dachs.larex.backend.controller.tag;

import de.uniwue.zpd.dachs.larex.backend.dto.BulkDeleteDto;
import de.uniwue.zpd.dachs.larex.backend.dto.TagSetDto;
import de.uniwue.zpd.dachs.larex.backend.service.tag.TagSetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/workspaces/{workspaceId}/tag-sets")
public class TagSetController {

    private final TagSetService tagSetService;

    public TagSetController(TagSetService tagSetService) {
        this.tagSetService = tagSetService;
    }

    @GetMapping
    public ResponseEntity<List<TagSetDto.SummaryResponse>> getTagSets(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<TagSetDto.SummaryResponse> tagSets;

        if (search != null && !search.trim().isEmpty()) {
            tagSets = tagSetService.searchTagSets(userId, workspaceId, search);
        } else {
            tagSets = tagSetService.getTagSets(userId, workspaceId);
        }

        return ResponseEntity.ok(tagSets);
    }

    @GetMapping("/{tagSetId}")
    public ResponseEntity<TagSetDto.Response> getTagSet(
            @PathVariable String workspaceId,
            @PathVariable String tagSetId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        TagSetDto.Response tagSet = tagSetService.getTagSet(userId, workspaceId, tagSetId);
        return ResponseEntity.ok(tagSet);
    }

    @PostMapping
    public ResponseEntity<TagSetDto.Response> createTagSet(
            @PathVariable String workspaceId,
            @Valid @RequestBody TagSetDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        TagSetDto.Response tagSet = tagSetService.createTagSet(userId, workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tagSet);
    }

    @PutMapping("/{tagSetId}")
    public ResponseEntity<TagSetDto.Response> updateTagSet(
            @PathVariable String workspaceId,
            @PathVariable String tagSetId,
            @Valid @RequestBody TagSetDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        TagSetDto.Response tagSet = tagSetService.updateTagSet(userId, workspaceId, tagSetId, request);
        return ResponseEntity.ok(tagSet);
    }

    @DeleteMapping("/{tagSetId}")
    public ResponseEntity<Void> deleteTagSet(
            @PathVariable String workspaceId,
            @PathVariable String tagSetId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        tagSetService.deleteTagSet(userId, workspaceId, tagSetId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<BulkDeleteDto.BulkDeleteResponse> bulkDeleteTagSets(
            @PathVariable String workspaceId,
            @Valid @RequestBody BulkDeleteDto.BulkDeleteRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(tagSetService.bulkDeleteTagSets(userId, workspaceId, request.ids()));
    }

    @GetMapping("/{tagSetId}/flattened")
    public ResponseEntity<List<TagSetDto.FlattenedTag>> getFlattenedTags(
            @PathVariable String workspaceId,
            @PathVariable String tagSetId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<TagSetDto.FlattenedTag> tags = tagSetService.getFlattenedTags(userId, workspaceId, tagSetId);
        return ResponseEntity.ok(tags);
    }

    @PostMapping("/{tagSetId}/validate")
    public ResponseEntity<TagSetDto.ValidationResult> validateTags(
            @PathVariable String workspaceId,
            @PathVariable String tagSetId,
            @RequestBody List<String> tagIds,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        TagSetDto.ValidationResult result = tagSetService.validateTags(userId, workspaceId, tagSetId, tagIds);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{tagSetId}/prune")
    public ResponseEntity<List<String>> pruneTags(
            @PathVariable String workspaceId,
            @PathVariable String tagSetId,
            @RequestBody List<String> tagIds,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<String> validTags = tagSetService.pruneTags(userId, workspaceId, tagSetId, tagIds);
        return ResponseEntity.ok(validTags);
    }

    @GetMapping("/{tagSetId}/tags/{tagId}/descendants")
    public ResponseEntity<Set<String>> getDescendantTagIds(
            @PathVariable String workspaceId,
            @PathVariable String tagSetId,
            @PathVariable String tagId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        Set<String> descendants = tagSetService.getDescendantTagIds(userId, workspaceId, tagSetId, tagId);
        return ResponseEntity.ok(descendants);
    }
}
