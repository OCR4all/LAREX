package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.config.ActionProperties;
import de.uniwue.zpd.dachs.larex.backend.config.security.GlobalAdminService;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDefinitionDocument;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionEndpointSecret;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionProcessorDefinition;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionEndpointSecretRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionProcessorDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class ActionEndpointSecretService {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final int GENERATED_SECRET_BYTES = 48;

    private final ActionEndpointSecretRepository secretRepository;
    private final ActionProcessorDefinitionRepository definitionRepository;
    private final GlobalAdminService globalAdminService;
    private final ActionProperties actionProperties;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public ActionEndpointSecretService(ActionEndpointSecretRepository secretRepository,
                                       ActionProcessorDefinitionRepository definitionRepository,
                                       GlobalAdminService globalAdminService,
                                       ActionProperties actionProperties,
                                       ObjectMapper objectMapper,
                                       Environment environment) {
        this(secretRepository, definitionRepository, globalAdminService, actionProperties, objectMapper, environment, Clock.systemUTC());
    }

    ActionEndpointSecretService(ActionEndpointSecretRepository secretRepository,
                                ActionProcessorDefinitionRepository definitionRepository,
                                GlobalAdminService globalAdminService,
                                ActionProperties actionProperties,
                                ObjectMapper objectMapper,
                                Environment environment,
                                Clock clock) {
        this.secretRepository = secretRepository;
        this.definitionRepository = definitionRepository;
        this.globalAdminService = globalAdminService;
        this.actionProperties = actionProperties;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ActionDto.EndpointSecretResponse> listSecrets() {
        requireGlobalAdmin();
        Map<String, ActionDto.EndpointSecretResponse> responses = new LinkedHashMap<>();
        secretRepository.findAllByOrderByRefAsc()
                .forEach(secret -> responses.put(secret.getRef(), toResponse(secret)));
        fallbackRefs().stream()
                .filter(ref -> !responses.containsKey(ref))
                .sorted()
                .forEach(ref -> responses.put(ref, fallbackResponse(ref)));
        return List.copyOf(responses.values());
    }

    public ActionDto.EndpointSecretRevealResponse createSecret(ActionDto.EndpointSecretRequest request, String userId) {
        requireGlobalAdmin();
        String ref = normalizeAndValidateRef(request.ref());
        if (secretRepository.existsByRef(ref)) {
            throw new IllegalArgumentException("Action endpoint secret already exists for ref: " + ref);
        }
        String plaintext = generateSecret();
        ActionEndpointSecret secret = new ActionEndpointSecret();
        secret.setRef(ref);
        secret.setDisplayName(trimToNull(request.displayName()));
        secret.setDescription(trimToNull(request.description()));
        secret.setCreatedByUserId(trimToNull(userId) == null ? "system" : userId);
        secret.setEncryptedSecret(encrypt(ref, plaintext));
        ActionEndpointSecret saved = secretRepository.save(secret);
        return new ActionDto.EndpointSecretRevealResponse(toResponse(saved), plaintext);
    }

    public ActionDto.EndpointSecretRevealResponse rotateSecret(String secretId) {
        requireGlobalAdmin();
        ActionEndpointSecret secret = requireSecret(secretId);
        String plaintext = generateSecret();
        secret.setEncryptedSecret(encrypt(secret.getRef(), plaintext));
        secret.setRotatedAt(LocalDateTime.now(clock));
        ActionEndpointSecret saved = secretRepository.save(secret);
        return new ActionDto.EndpointSecretRevealResponse(toResponse(saved), plaintext);
    }

    public void deleteSecret(String secretId) {
        requireGlobalAdmin();
        ActionEndpointSecret secret = requireSecret(secretId);
        List<String> referencingProcessors = referencingProcessorKeys(secret.getRef());
        if (!referencingProcessors.isEmpty()) {
            throw new IllegalArgumentException("Action endpoint secret " + secret.getRef()
                    + " is still referenced by processor definition(s): "
                    + String.join(", ", referencingProcessors));
        }
        secretRepository.delete(secret);
    }

    @Transactional(readOnly = true)
    public boolean hasDbSecret(String secretRef) {
        String ref = ActionEndpointSecretRef.normalizeForStorage(secretRef);
        return ref != null && secretRepository.existsByRef(ref);
    }

    public String resolveDbSecretForUse(String secretRef) {
        String ref = ActionEndpointSecretRef.normalizeForStorage(secretRef);
        if (ref == null) {
            return null;
        }
        return secretRepository.findByRef(ref)
                .map(secret -> {
                    String plaintext = decrypt(secret.getRef(), secret.getEncryptedSecret());
                    secret.setLastUsedAt(LocalDateTime.now(clock));
                    return plaintext;
                })
                .orElse(null);
    }

    public String normalizeAndValidateRef(String secretRef) {
        if (!ActionEndpointSecretRef.isValid(secretRef)) {
            throw new IllegalArgumentException("Action endpoint secret ref must match " + ActionEndpointSecretRef.PATTERN);
        }
        return ActionEndpointSecretRef.normalizeForStorage(secretRef);
    }

    public ActionDto.EndpointSecretResponse toResponse(ActionEndpointSecret secret) {
        return new ActionDto.EndpointSecretResponse(
                secret.getId(),
                secret.getRef(),
                "LAREX_ACTION_ENDPOINT_SECRET_" + ActionEndpointSecretRef.normalizeForEnv(secret.getRef()),
                secret.getDisplayName(),
                secret.getDescription(),
                secret.getCreatedByUserId(),
                secret.getCreated(),
                secret.getUpdated(),
                secret.getLastUsedAt(),
                secret.getRotatedAt(),
                "DATABASE"
        );
    }

    private ActionDto.EndpointSecretResponse fallbackResponse(String ref) {
        return new ActionDto.EndpointSecretResponse(
                null,
                ref,
                "LAREX_ACTION_ENDPOINT_SECRET_" + ActionEndpointSecretRef.normalizeForEnv(ref),
                null,
                "Configured in deployment environment",
                null,
                null,
                null,
                null,
                null,
                "ENV_FALLBACK"
        );
    }

    private Set<String> fallbackRefs() {
        Set<String> refs = new LinkedHashSet<>();
        actionProperties.getEndpointSecrets().keySet().stream()
                .map(ActionEndpointSecretRef::normalizeForStorage)
                .filter(ref -> ref != null && configuredFallbackSecret(ref) != null)
                .forEach(refs::add);
        definitionRepository.findAllByOrderByNameAsc().stream()
                .map(this::referencedSecretRef)
                .filter(ref -> ref != null && configuredFallbackSecret(ref) != null)
                .forEach(refs::add);
        return refs;
    }

    private ActionEndpointSecret requireSecret(String secretId) {
        return secretRepository.findById(secretId)
                .orElseThrow(() -> new IllegalArgumentException("Action endpoint secret not found"));
    }

    private List<String> referencingProcessorKeys(String ref) {
        return definitionRepository.findAllByOrderByNameAsc().stream()
                .filter(definition -> referencesSecretRef(definition, ref))
                .map(ActionProcessorDefinition::getProcessorKey)
                .toList();
    }

    private boolean referencesSecretRef(ActionProcessorDefinition definition, String ref) {
        return ref.equals(referencedSecretRef(definition));
    }

    private String referencedSecretRef(ActionProcessorDefinition definition) {
        try {
            ActionDefinitionDocument document = objectMapper.readValue(definition.getParsedJson(), ActionDefinitionDocument.class);
            ActionDefinitionDocument.Endpoint endpoint = document.endpoint();
            ActionDefinitionDocument.EndpointAuth auth = endpoint == null ? null : endpoint.auth();
            String secretRef = auth == null ? null : auth.secretRef();
            return ActionEndpointSecretRef.normalizeForStorage(secretRef);
        } catch (JacksonException e) {
            return null;
        }
    }

    private String generateSecret() {
        byte[] secret = new byte[GENERATED_SECRET_BYTES];
        secureRandom.nextBytes(secret);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
    }

    private String encrypt(String ref, String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(ref.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                    + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt Action endpoint secret", e);
        }
    }

    private String decrypt(String ref, String encryptedSecret) {
        try {
            String[] parts = encryptedSecret.split(":", 3);
            if (parts.length != 3 || !"v1".equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported encrypted Action endpoint secret format");
            }
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(ref.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not decrypt Action endpoint secret", e);
        }
    }

    private SecretKeySpec key() {
        String configuredKey = actionProperties.getEndpointSecretEncryptionKey();
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException("larex.actions.endpoint-secret-encryption-key must be configured to manage database-backed Action endpoint secrets");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(configuredKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Could not derive Action endpoint secret encryption key", e);
        }
    }

    private String configuredFallbackSecret(String secretRef) {
        if (secretRef == null || secretRef.isBlank()) {
            return null;
        }
        String exactSecret = actionProperties.getEndpointSecrets().get(secretRef);
        if (exactSecret != null && !exactSecret.isBlank()) {
            return exactSecret;
        }
        String normalizedRef = ActionEndpointSecretRef.normalizeForEnv(secretRef);
        if (normalizedRef != null) {
            String configuredSecret = actionProperties.getEndpointSecrets().entrySet().stream()
                    .filter(entry -> normalizedRef.equals(ActionEndpointSecretRef.normalizeForEnv(entry.getKey())))
                    .map(Map.Entry::getValue)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);
            if (configuredSecret != null) {
                return configuredSecret;
            }
        }

        String propertyValue = environment.getProperty("larex.actions.endpoint-secrets." + secretRef);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        propertyValue = environment.getProperty("larex.actions.endpoint-secrets[" + secretRef + "]");
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }

        String envName = "LAREX_ACTION_ENDPOINT_SECRET_" + normalizedRef;
        String envValue = environment.getProperty(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        String pluralEnvName = "LAREX_ACTIONS_ENDPOINT_SECRETS_" + normalizedRef;
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

    private void requireGlobalAdmin() {
        if (!globalAdminService.isGlobalAdmin()) {
            throw new SecurityException("Global admin access is required to manage Action endpoint secrets");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
