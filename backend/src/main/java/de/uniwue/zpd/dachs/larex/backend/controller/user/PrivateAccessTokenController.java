package de.uniwue.zpd.dachs.larex.backend.controller.user;

import de.uniwue.zpd.dachs.larex.backend.dto.PrivateAccessTokenDto;
import de.uniwue.zpd.dachs.larex.backend.service.user.PrivateAccessTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/profile/private-access-tokens")
public class PrivateAccessTokenController {

    private final PrivateAccessTokenService privateAccessTokenService;

    public PrivateAccessTokenController(PrivateAccessTokenService privateAccessTokenService) {
        this.privateAccessTokenService = privateAccessTokenService;
    }

    @GetMapping
    public ResponseEntity<List<PrivateAccessTokenDto.SummaryResponse>> listTokens(
            @AuthenticationPrincipal(expression = "subject") String userId) {
        return ResponseEntity.ok(privateAccessTokenService.listTokensForUser(userId));
    }

    @PostMapping
    public ResponseEntity<PrivateAccessTokenDto.CreatedResponse> createToken(
            @Valid @RequestBody PrivateAccessTokenDto.CreateRequest request,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        PrivateAccessTokenDto.CreatedResponse response = privateAccessTokenService.createTokenForUser(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{tokenId}/revoke")
    public ResponseEntity<Void> revokeToken(
            @PathVariable String tokenId,
            @AuthenticationPrincipal(expression = "subject") String userId) {
        privateAccessTokenService.revokeTokenForUser(userId, tokenId);
        return ResponseEntity.noContent().build();
    }
}
