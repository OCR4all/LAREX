package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class ActionEndpointAuthService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HEADER_AUTH = "X-LAREX-Action-Auth";
    private static final String HEADER_PROCESSOR = "X-LAREX-Action-Processor";
    private static final String HEADER_RUN = "X-LAREX-Action-Run-Id";
    private static final String HEADER_TIMESTAMP = "X-LAREX-Action-Timestamp";
    private static final String HEADER_NONCE = "X-LAREX-Action-Nonce";
    private static final String HEADER_BODY_HASH = "X-LAREX-Action-Body-SHA256";
    private static final String HEADER_SIGNATURE = "X-LAREX-Action-Signature";

    private final ActionProperties actionProperties;
    private final Environment environment;
    private final Clock clock;

    @Autowired
    public ActionEndpointAuthService(ActionProperties actionProperties, Environment environment) {
        this(actionProperties, environment, Clock.systemUTC());
    }

    ActionEndpointAuthService(ActionProperties actionProperties, Environment environment, Clock clock) {
        this.actionProperties = actionProperties;
        this.environment = environment;
        this.clock = clock;
    }

    public Map<String, String> buildDispatchHeaders(ActionDefinitionDocument.EndpointAuth auth,
                                                    String processorKey,
                                                    String runId,
                                                    URI endpointUri,
                                                    String nonce,
                                                    String body) {
        String type = normalizeAuthType(auth);
        if ("none".equals(type)) {
            return Map.of();
        }
        if (!"hmac".equals(type)) {
            throw new IllegalArgumentException("Unsupported Action endpoint auth type: " + type);
        }

        String secretRef = auth == null ? null : auth.secretRef();
        String secret = resolveSecret(secretRef);
        String timestamp = Instant.now(clock).toString();
        String bodyHash = sha256Base64Url(body);
        String canonical = canonicalDispatchRequest(
                "POST",
                endpointUri,
                runId,
                processorKey,
                timestamp,
                nonce,
                bodyHash
        );
        String signature = hmacSha256Base64Url(secret, canonical);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_AUTH, "hmac-sha256;v=1");
        headers.put(HEADER_PROCESSOR, processorKey);
        headers.put(HEADER_RUN, runId);
        headers.put(HEADER_TIMESTAMP, timestamp);
        headers.put(HEADER_NONCE, nonce);
        headers.put(HEADER_BODY_HASH, bodyHash);
        headers.put(HEADER_SIGNATURE, "v1=" + signature);
        return headers;
    }

    public boolean hasSecret(String secretRef) {
        return lookupSecret(secretRef) != null;
    }

    public String envNameForSecretRef(String secretRef) {
        String normalized = normalizeSecretRefForEnv(secretRef);
        if (normalized == null) {
            return "LAREX_ACTION_ENDPOINT_SECRET_<SECRET_REF>";
        }
        return "LAREX_ACTION_ENDPOINT_SECRET_" + normalized;
    }

    public String normalizeAuthType(ActionDefinitionDocument.EndpointAuth auth) {
        if (auth == null || auth.type() == null || auth.type().isBlank()) {
            return "none";
        }
        return auth.type().trim().toLowerCase(Locale.ROOT);
    }

    private String resolveSecret(String secretRef) {
        String secret = lookupSecret(secretRef);
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Action endpoint HMAC secret is not configured for secretRef: "
                    + secretRef + " (" + envNameForSecretRef(secretRef) + " or " + pluralEnvNameForSecretRef(secretRef) + ")");
        }
        return secret;
    }

    private String lookupSecret(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            return null;
        }
        String configuredSecret = configuredEndpointSecret(secretRef);
        if (configuredSecret != null && !configuredSecret.isBlank()) {
            return configuredSecret;
        }

        String propertyValue = environment.getProperty("larex.actions.endpoint-secrets." + secretRef);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        propertyValue = environment.getProperty("larex.actions.endpoint-secrets[" + secretRef + "]");
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }

        String envName = envNameForSecretRef(secretRef);
        String envValue = environment.getProperty(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String pluralEnvName = pluralEnvNameForSecretRef(secretRef);
        envValue = environment.getProperty(pluralEnvName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        envValue = System.getenv(pluralEnvName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return null;
    }

    private String configuredEndpointSecret(String secretRef) {
        Map<String, String> endpointSecrets = actionProperties.getEndpointSecrets();
        String exactSecret = endpointSecrets.get(secretRef);
        if (exactSecret != null && !exactSecret.isBlank()) {
            return exactSecret;
        }

        String normalizedRef = normalizeSecretRefForEnv(secretRef);
        if (normalizedRef == null) {
            return null;
        }
        return endpointSecrets.entrySet().stream()
                .filter(entry -> normalizedRef.equals(normalizeSecretRefForEnv(entry.getKey())))
                .map(Map.Entry::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String pluralEnvNameForSecretRef(String secretRef) {
        String normalized = normalizeSecretRefForEnv(secretRef);
        if (normalized == null) {
            return "LAREX_ACTIONS_ENDPOINT_SECRETS_<SECRET_REF>";
        }
        return "LAREX_ACTIONS_ENDPOINT_SECRETS_" + normalized;
    }

    private String canonicalDispatchRequest(String method,
                                            URI endpointUri,
                                            String runId,
                                            String processorKey,
                                            String timestamp,
                                            String nonce,
                                            String bodyHash) {
        String pathAndQuery = endpointUri.getRawPath() == null || endpointUri.getRawPath().isBlank()
                ? "/"
                : endpointUri.getRawPath();
        if (endpointUri.getRawQuery() != null && !endpointUri.getRawQuery().isBlank()) {
            pathAndQuery += "?" + endpointUri.getRawQuery();
        }
        return String.join("\n",
                "larex-action-dispatch-v1",
                method.toUpperCase(Locale.ROOT),
                pathAndQuery,
                runId,
                processorKey,
                timestamp,
                nonce,
                bodyHash
        );
    }

    private String hmacSha256Base64Url(String secret, String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not sign Action dispatch request", e);
        }
    }

    private String sha256Base64Url(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash Action dispatch request body", e);
        }
    }

    private String normalizeSecretRefForEnv(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            return null;
        }
        return secretRef.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }
}
