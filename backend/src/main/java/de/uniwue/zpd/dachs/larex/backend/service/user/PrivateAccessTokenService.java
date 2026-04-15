package de.uniwue.zpd.dachs.larex.backend.service.user;

import de.uniwue.zpd.dachs.larex.backend.config.auth.AuthProvisioningProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.PrivateAccessTokenDto;
import de.uniwue.zpd.dachs.larex.backend.entity.UserPrivateAccessToken;
import de.uniwue.zpd.dachs.larex.backend.repository.user.UserPrivateAccessTokenRepository;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class PrivateAccessTokenService {

    public static final String SCOPE_XML_READ = "xml:read";
    public static final String SCOPE_XML_WRITE = "xml:write";

    private static final Set<String> ALLOWED_SCOPES = Set.of(SCOPE_XML_READ, SCOPE_XML_WRITE);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserPrivateAccessTokenRepository userPrivateAccessTokenRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final AuthProvisioningProperties authProvisioningProperties;

    public PrivateAccessTokenService(UserPrivateAccessTokenRepository userPrivateAccessTokenRepository,
                                     WorkspaceAccessService workspaceAccessService,
                                     AuthProvisioningProperties authProvisioningProperties) {
        this.userPrivateAccessTokenRepository = userPrivateAccessTokenRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.authProvisioningProperties = authProvisioningProperties;
    }

    @Transactional(readOnly = true)
    public List<PrivateAccessTokenDto.SummaryResponse> listTokensForUser(String userId) {
        requirePrivateAccessTokenEligible(userId);
        LocalDateTime now = LocalDateTime.now();

        return userPrivateAccessTokenRepository.findByOwnerUserIdOrderByCreatedAtDesc(userId).stream()
                .map(token -> toSummaryResponse(token, now))
                .toList();
    }

    public PrivateAccessTokenDto.CreatedResponse createTokenForUser(String userId, PrivateAccessTokenDto.CreateRequest request) {
        requirePrivateAccessTokenEligible(userId);

        String workspaceId = normalizeRequired(request.workspaceId(), "Workspace ID is required");
        String name = normalizeRequired(request.name(), "Token name is required");
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = resolveExpiresAt(now, request.expiresAt());

        long activeCount = userPrivateAccessTokenRepository.countActiveByOwnerAndWorkspace(userId, workspaceId, now);
        if (activeCount >= maxActiveTokensPerWorkspace()) {
            throw new IllegalArgumentException("Maximum number of active private access tokens reached for this workspace");
        }

        List<String> normalizedScopes = normalizeScopes(request.scopes());
        String rawSecret = "lrx_pat_" + generateOpaqueToken(32);

        UserPrivateAccessToken token = new UserPrivateAccessToken();
        token.setOwnerUserId(userId);
        token.setWorkspaceId(workspaceId);
        token.setName(name);
        token.setSecretHash(computeSha256(rawSecret.getBytes(StandardCharsets.UTF_8)));
        token.setSecretPrefix(rawSecret.substring(0, Math.min(12, rawSecret.length())));
        token.setScopes(serializeScopes(normalizedScopes));
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(null);
        token.setLastUsedAt(null);

        UserPrivateAccessToken saved = userPrivateAccessTokenRepository.save(token);

        return new PrivateAccessTokenDto.CreatedResponse(
                saved.getId(),
                saved.getWorkspaceId(),
                saved.getName(),
                normalizedScopes,
                saved.getCreatedAt(),
                saved.getExpiresAt(),
                rawSecret
        );
    }

    public void revokeTokenForUser(String userId, String tokenId) {
        requirePrivateAccessTokenEligible(userId);

        UserPrivateAccessToken token = userPrivateAccessTokenRepository.findByIdAndOwnerUserId(tokenId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Private access token not found"));

        if (token.getRevokedAt() == null) {
            token.setRevokedAt(LocalDateTime.now());
            userPrivateAccessTokenRepository.save(token);
        }
    }

    public Optional<PrivateAccessTokenAuthContext> authenticateBearerToken(String authorizationHeader) {
        String rawToken = extractBearerToken(authorizationHeader);
        if (rawToken == null) {
            return Optional.empty();
        }

        String hash = computeSha256(rawToken.getBytes(StandardCharsets.UTF_8));
        Optional<UserPrivateAccessToken> tokenOpt = userPrivateAccessTokenRepository.findBySecretHash(hash);
        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }

        UserPrivateAccessToken token = tokenOpt.get();
        if (!isPrivateAccessTokenEligible(token.getOwnerUserId())) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        if (!isTokenActive(token, now)) {
            return Optional.empty();
        }

        token.setLastUsedAt(now);
        userPrivateAccessTokenRepository.save(token);

        return Optional.of(new PrivateAccessTokenAuthContext(
                token.getId(),
                token.getOwnerUserId(),
                token.getWorkspaceId(),
                parseScopes(token.getScopes())
        ));
    }

    private PrivateAccessTokenDto.SummaryResponse toSummaryResponse(UserPrivateAccessToken token, LocalDateTime now) {
        return new PrivateAccessTokenDto.SummaryResponse(
                token.getId(),
                token.getWorkspaceId(),
                token.getName(),
                token.getSecretPrefix(),
                parseScopes(token.getScopes()),
                token.getCreatedAt(),
                token.getExpiresAt(),
                token.getRevokedAt(),
                token.getLastUsedAt(),
                isTokenActive(token, now)
        );
    }

    private boolean isTokenActive(UserPrivateAccessToken token, LocalDateTime now) {
        return token.getRevokedAt() == null
                && token.getExpiresAt() != null
                && token.getExpiresAt().isAfter(now);
    }

    private LocalDateTime resolveExpiresAt(LocalDateTime now, LocalDateTime requestedExpiresAt) {
        LocalDateTime defaultExpiry = now.plusDays(defaultExpiryDays());
        LocalDateTime resolved = requestedExpiresAt == null ? defaultExpiry : requestedExpiresAt;
        LocalDateTime maxAllowed = now.plusDays(maxExpiryDays());

        if (!resolved.isAfter(now)) {
            throw new IllegalArgumentException("Expiration must be in the future");
        }
        if (resolved.isAfter(maxAllowed)) {
            throw new IllegalArgumentException("Expiration exceeds maximum allowed lifetime");
        }

        return resolved;
    }

    private List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new IllegalArgumentException("At least one scope is required");
        }

        Set<String> normalized = scopes.stream()
                .filter(scope -> scope != null && !scope.isBlank())
                .map(scope -> scope.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("At least one scope is required");
        }

        if (!ALLOWED_SCOPES.containsAll(normalized)) {
            throw new IllegalArgumentException("Invalid scope requested");
        }

        return normalized.stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private List<String> parseScopes(String scopesSerialized) {
        if (scopesSerialized == null || scopesSerialized.isBlank()) {
            return List.of();
        }

        return Set.copyOf(List.of(scopesSerialized.split(","))).stream()
                .map(String::trim)
                .filter(scope -> !scope.isBlank())
                .sorted()
                .toList();
    }

    private String serializeScopes(List<String> scopes) {
        return String.join(",", scopes);
    }

    private String normalizeRequired(String value, String errorMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }

    private void requirePrivateAccessTokenEligible(String userId) {
        if (!isPrivateAccessTokenEligible(userId)) {
            throw new SecurityException(privateAccessTokenEligibilityMessage());
        }
    }

    private boolean isPrivateAccessTokenEligible(String userId) {
        List<String> allowlist = normalizedAllowlist();
        if (allowlist.isEmpty()) {
            return false;
        }
        return allowlist.contains(userId);
    }

    private String privateAccessTokenEligibilityMessage() {
        if (normalizedAllowlist().isEmpty()) {
            return "Private access tokens are disabled";
        }
        return "Private access tokens are not enabled for this account";
    }

    private List<String> normalizedAllowlist() {
        List<String> raw = authProvisioningProperties.getPrivateAccessTokens().getAllowedUserIds();
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        List<String> normalized = new ArrayList<>();
        for (String entry : raw) {
            if (entry == null) {
                continue;
            }
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                normalized.add(trimmed);
            }
        }
        return normalized;
    }

    private int defaultExpiryDays() {
        int configured = authProvisioningProperties.getPrivateAccessTokens().getDefaultExpiryDays();
        return configured <= 0 ? 30 : configured;
    }

    private int maxExpiryDays() {
        int configured = authProvisioningProperties.getPrivateAccessTokens().getMaxExpiryDays();
        return configured <= 0 ? 90 : configured;
    }

    private int maxActiveTokensPerWorkspace() {
        int configured = authProvisioningProperties.getPrivateAccessTokens().getMaxActiveTokensPerWorkspace();
        return configured <= 0 ? 5 : configured;
    }

    private String generateOpaqueToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String computeSha256(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        if (!authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        return token.isBlank() ? null : token;
    }

    public record PrivateAccessTokenAuthContext(
            String tokenId,
            String ownerUserId,
            String workspaceId,
            List<String> scopes
    ) {
        public boolean hasScope(String requiredScope) {
            return scopes != null && scopes.contains(requiredScope);
        }
    }
}
