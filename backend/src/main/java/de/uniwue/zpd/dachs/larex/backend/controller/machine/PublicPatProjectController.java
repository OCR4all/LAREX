package de.uniwue.zpd.dachs.larex.backend.controller.machine;

import de.uniwue.zpd.dachs.larex.backend.dto.PatProjectDto;
import de.uniwue.zpd.dachs.larex.backend.service.machine.PatProjectReadService;
import de.uniwue.zpd.dachs.larex.backend.service.user.PrivateAccessTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/public/pat")
public class PublicPatProjectController {

    private final PrivateAccessTokenService privateAccessTokenService;
    private final PatProjectReadService patProjectReadService;

    public PublicPatProjectController(PrivateAccessTokenService privateAccessTokenService,
                                      PatProjectReadService patProjectReadService) {
        this.privateAccessTokenService = privateAccessTokenService;
        this.patProjectReadService = patProjectReadService;
    }

    @GetMapping("/projects")
    public ResponseEntity<?> listProjects(
            @RequestParam(value = "workspaceId", required = false) String workspaceId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        Optional<PrivateAccessTokenService.PrivateAccessTokenAuthContext> authContextOpt =
                privateAccessTokenService.authenticateBearerToken(authorizationHeader);
        if (authContextOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PrivateAccessTokenService.PrivateAccessTokenAuthContext authContext = authContextOpt.get();
        if (!authContext.hasScope(PrivateAccessTokenService.SCOPE_XML_READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String tokenWorkspaceId = authContext.workspaceId();
        if (workspaceId != null && !workspaceId.isBlank() && !tokenWorkspaceId.equals(workspaceId.trim())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<PatProjectDto.ProjectSummaryResponse> response = patProjectReadService.listProjects(tokenWorkspaceId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/workspaces/{workspaceId}/projects")
    public ResponseEntity<?> listProjectsByWorkspace(
            @PathVariable String workspaceId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        Optional<PrivateAccessTokenService.PrivateAccessTokenAuthContext> authContextOpt =
                privateAccessTokenService.authenticateBearerToken(authorizationHeader);
        if (authContextOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PrivateAccessTokenService.PrivateAccessTokenAuthContext authContext = authContextOpt.get();
        if (!authContext.hasScope(PrivateAccessTokenService.SCOPE_XML_READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!authContext.workspaceId().equals(workspaceId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        List<PatProjectDto.ProjectSummaryResponse> response = patProjectReadService.listProjects(workspaceId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/projects/{projectId}")
    public ResponseEntity<?> getProjectDetails(
            @PathVariable String projectId,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        Optional<PrivateAccessTokenService.PrivateAccessTokenAuthContext> authContextOpt =
                privateAccessTokenService.authenticateBearerToken(authorizationHeader);
        if (authContextOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PrivateAccessTokenService.PrivateAccessTokenAuthContext authContext = authContextOpt.get();
        if (!authContext.hasScope(PrivateAccessTokenService.SCOPE_XML_READ)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return patProjectReadService.getProjectDetail(authContext.workspaceId(), projectId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
