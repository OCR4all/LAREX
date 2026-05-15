package de.uniwue.zpd.dachs.larex.backend.controller.codec;

import de.uniwue.zpd.dachs.larex.backend.dto.BulkDeleteDto;
import de.uniwue.zpd.dachs.larex.backend.dto.CodecDto;
import de.uniwue.zpd.dachs.larex.backend.service.codec.CodecService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/codecs")
public class CodecController {

    private final CodecService codecService;

    public CodecController(CodecService codecService) {
        this.codecService = codecService;
    }

    @GetMapping
    public ResponseEntity<List<CodecDto.SummaryResponse>> getCodecs(
            @PathVariable String workspaceId,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        List<CodecDto.SummaryResponse> codecs;

        if (search != null && !search.trim().isEmpty()) {
            codecs = codecService.searchCodecs(userId, workspaceId, search);
        } else {
            codecs = codecService.getCodecs(userId, workspaceId);
        }

        return ResponseEntity.ok(codecs);
    }

    @GetMapping("/{codecId}")
    public ResponseEntity<CodecDto.Response> getCodec(
            @PathVariable String workspaceId,
            @PathVariable String codecId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        CodecDto.Response codec = codecService.getCodec(userId, workspaceId, codecId);
        return ResponseEntity.ok(codec);
    }

    @PostMapping
    public ResponseEntity<CodecDto.Response> createCodec(
            @PathVariable String workspaceId,
            @Valid @RequestBody CodecDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        CodecDto.Response codec = codecService.createCodec(userId, workspaceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(codec);
    }

    @PutMapping("/{codecId}")
    public ResponseEntity<CodecDto.Response> updateCodec(
            @PathVariable String workspaceId,
            @PathVariable String codecId,
            @Valid @RequestBody CodecDto.CreateOrUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        CodecDto.Response codec = codecService.updateCodec(userId, workspaceId, codecId, request);
        return ResponseEntity.ok(codec);
    }

    @DeleteMapping("/{codecId}")
    public ResponseEntity<Void> deleteCodec(
            @PathVariable String workspaceId,
            @PathVariable String codecId,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        codecService.deleteCodec(userId, workspaceId, codecId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<BulkDeleteDto.BulkDeleteResponse> bulkDeleteCodecs(
            @PathVariable String workspaceId,
            @Valid @RequestBody BulkDeleteDto.BulkDeleteRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(codecService.bulkDeleteCodecs(userId, workspaceId, request.ids()));
    }

    // Character management endpoints
    @PostMapping("/{codecId}/characters")
    public ResponseEntity<CodecDto.Response> addCharacter(
            @PathVariable String workspaceId,
            @PathVariable String codecId,
            @Valid @RequestBody CodecDto.AddCharacterRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        CodecDto.Response codec = codecService.addCharacter(userId, workspaceId, codecId, request.character());
        return ResponseEntity.ok(codec);
    }

    @PostMapping("/generate-from-sources")
    public ResponseEntity<CodecDto.GenerateFromSourcesResponse> generateFromSources(
            @PathVariable String workspaceId,
            @Valid @RequestBody CodecDto.GenerateFromSourcesRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        CodecDto.GenerateFromSourcesResponse response = codecService.generateFromSources(userId, workspaceId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{codecId}/validate-against-sources")
    public ResponseEntity<CodecDto.ValidateAgainstSourcesResponse> validateAgainstSources(
            @PathVariable String workspaceId,
            @PathVariable String codecId,
            @Valid @RequestBody CodecDto.ValidateAgainstSourcesRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {

        CodecDto.ValidateAgainstSourcesResponse response = codecService.validateAgainstSources(userId, workspaceId, codecId, request);
        return ResponseEntity.ok(response);
    }
}
