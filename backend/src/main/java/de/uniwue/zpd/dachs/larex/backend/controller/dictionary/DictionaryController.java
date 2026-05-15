package de.uniwue.zpd.dachs.larex.backend.controller.dictionary;

import de.uniwue.zpd.dachs.larex.backend.dto.BulkDeleteDto;
import de.uniwue.zpd.dachs.larex.backend.dto.DictionaryDto;
import de.uniwue.zpd.dachs.larex.backend.service.dictionary.DictionaryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/workspaces/{workspaceId}/dictionaries")
public class DictionaryController {

    private final DictionaryService dictionaryService;

    public DictionaryController(DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @GetMapping
    public ResponseEntity<List<DictionaryDto.SummaryResponse>> getDictionaries(@PathVariable String workspaceId,
                                                                               @RequestParam(required = false) String search,
                                                                               @AuthenticationPrincipal(expression = "subject") String userId) {
        List<DictionaryDto.SummaryResponse> dictionaries = search != null && !search.trim().isEmpty()
                ? dictionaryService.searchDictionaries(userId, workspaceId, search)
                : dictionaryService.getDictionaries(userId, workspaceId);
        return ResponseEntity.ok(dictionaries);
    }

    @GetMapping("/{dictionaryId}")
    public ResponseEntity<DictionaryDto.Response> getDictionary(@PathVariable String workspaceId,
                                                                @PathVariable String dictionaryId,
                                                                @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(dictionaryService.getDictionary(userId, workspaceId, dictionaryId));
    }

    @PostMapping
    public ResponseEntity<DictionaryDto.Response> createDictionary(@PathVariable String workspaceId,
                                                                   @Valid @RequestBody DictionaryDto.CreateOrUpdateRequest request,
                                                                   @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dictionaryService.createDictionary(userId, workspaceId, request));
    }

    @PutMapping("/{dictionaryId}")
    public ResponseEntity<DictionaryDto.Response> updateDictionary(@PathVariable String workspaceId,
                                                                   @PathVariable String dictionaryId,
                                                                   @Valid @RequestBody DictionaryDto.CreateOrUpdateRequest request,
                                                                   @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(dictionaryService.updateDictionary(userId, workspaceId, dictionaryId, request));
    }

    @DeleteMapping("/{dictionaryId}")
    public ResponseEntity<Void> deleteDictionary(@PathVariable String workspaceId,
                                                 @PathVariable String dictionaryId,
                                                 @AuthenticationPrincipal(expression = "subject") String userId) {
        dictionaryService.deleteDictionary(userId, workspaceId, dictionaryId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/bulk")
    public ResponseEntity<BulkDeleteDto.BulkDeleteResponse> bulkDeleteDictionaries(
            @PathVariable String workspaceId,
            @Valid @RequestBody BulkDeleteDto.BulkDeleteRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(dictionaryService.bulkDeleteDictionaries(userId, workspaceId, request.ids()));
    }

    @GetMapping("/{dictionaryId}/entries")
    public ResponseEntity<DictionaryDto.EntryPageResponse> getEntries(@PathVariable String workspaceId,
                                                                      @PathVariable String dictionaryId,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "100") int size,
                                                                      @RequestParam(required = false) String search,
                                                                      @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(dictionaryService.getEntries(userId, workspaceId, dictionaryId, page, size, search));
    }

    @PostMapping("/{dictionaryId}/entries")
    public ResponseEntity<DictionaryDto.EntryResponse> addEntry(@PathVariable String workspaceId,
                                                                @PathVariable String dictionaryId,
                                                                @Valid @RequestBody DictionaryDto.EntryCreateOrUpdateRequest request,
                                                                @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(dictionaryService.addEntry(userId, workspaceId, dictionaryId, request));
    }

    @PutMapping("/{dictionaryId}/entries/{entryId}")
    public ResponseEntity<DictionaryDto.EntryResponse> updateEntry(@PathVariable String workspaceId,
                                                                   @PathVariable String dictionaryId,
                                                                   @PathVariable String entryId,
                                                                   @Valid @RequestBody DictionaryDto.EntryCreateOrUpdateRequest request,
                                                                   @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(dictionaryService.updateEntry(userId, workspaceId, dictionaryId, entryId, request));
    }

    @DeleteMapping("/{dictionaryId}/entries/{entryId}")
    public ResponseEntity<Void> deleteEntry(@PathVariable String workspaceId,
                                            @PathVariable String dictionaryId,
                                            @PathVariable String entryId,
                                            @AuthenticationPrincipal(expression = "subject") String userId) {
        dictionaryService.deleteEntry(userId, workspaceId, dictionaryId, entryId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DictionaryDto.Response> createDictionaryFromImport(@PathVariable String workspaceId,
                                                                             @RequestParam("file") MultipartFile file,
                                                                             @RequestParam(required = false) DictionaryDto.ImportFormat format,
                                                                             @RequestParam(required = false) String name,
                                                                             @RequestParam(required = false) String description,
                                                                             @RequestParam(required = false) List<String> tags,
                                                                             @RequestParam(required = false) Boolean caseSensitive,
                                                                             @RequestParam(required = false) String unicodeNormalization,
                                                                             @RequestParam(required = false) Boolean locked,
                                                                             @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(dictionaryService.createDictionaryFromImport(
                userId,
                workspaceId,
                file,
                format == null ? DictionaryDto.ImportFormat.AUTO : format,
                name,
                description,
                tags,
                caseSensitive,
                unicodeNormalization,
                locked
        ));
    }

    @PostMapping(value = "/{dictionaryId}/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DictionaryDto.ImportResult> importIntoDictionary(@PathVariable String workspaceId,
                                                                           @PathVariable String dictionaryId,
                                                                           @RequestParam("file") MultipartFile file,
                                                                           @RequestParam(required = false) DictionaryDto.ImportFormat format,
                                                                           @RequestParam(required = false) DictionaryDto.ImportMode mode,
                                                                           @AuthenticationPrincipal(expression = "subject") String userId) throws IOException {
        return ResponseEntity.ok(dictionaryService.importIntoDictionary(
                userId,
                workspaceId,
                dictionaryId,
                file,
                format == null ? DictionaryDto.ImportFormat.AUTO : format,
                mode == null ? DictionaryDto.ImportMode.APPEND : mode
        ));
    }

    @PostMapping("/{dictionaryId}/validate-against-sources")
    public ResponseEntity<DictionaryDto.ValidateAgainstSourcesResponse> validateAgainstSources(@PathVariable String workspaceId,
                                                                                               @PathVariable String dictionaryId,
                                                                                               @Valid @RequestBody DictionaryDto.ValidateAgainstSourcesRequest request,
                                                                                               @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(dictionaryService.validateAgainstSources(userId, workspaceId, dictionaryId, request));
    }

    @PostMapping("/{dictionaryId}/check-tokens")
    public ResponseEntity<DictionaryDto.CheckTokensResponse> checkTokens(@PathVariable String workspaceId,
                                                                         @PathVariable String dictionaryId,
                                                                         @Valid @RequestBody DictionaryDto.CheckTokensRequest request,
                                                                         @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(dictionaryService.checkTokens(userId, workspaceId, dictionaryId, request));
    }
}
