package de.uniwue.zpd.dachs.larex.backend.service.notification;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.config.NotificationBridgeProperties;
import de.uniwue.zpd.dachs.larex.backend.entity.Notification;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationBridgeClient {

    private static final Logger logger = LoggerFactory.getLogger(NotificationBridgeClient.class);

    private final ObjectMapper objectMapper;
    private final NotificationBridgeProperties properties;
    private final HttpClient httpClient;

    public NotificationBridgeClient(ObjectMapper objectMapper,
                                    NotificationBridgeProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public void pushNotification(Notification notification, String source) {
        if (!properties.isEnabled() || notification == null) {
            return;
        }
        if (!properties.isConfigured()) {
            logger.debug("Notification bridge disabled because URL or secret is missing");
            return;
        }

        pushPayload(new NotificationBridgePayload(notification.getUserId(), notification, source), source);
    }

    public void pushActionEvent(String type, Map<String, Object> eventPayload, String source) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!properties.isConfigured()) {
            logger.debug("Notification bridge disabled because URL or secret is missing");
            return;
        }
        if (!"ACTION_RUN_UPDATED".equals(type) && !"ACTION_PAGE_RESULT_IMPORTED".equals(type)) {
            throw new IllegalArgumentException("Unsupported Action realtime event type: " + type);
        }
        pushPayload(new ActionEventBridgePayload(new BridgeEvent(type, eventPayload), source), source);
    }

    public void pushJobEvent(String userId, Map<String, Object> eventPayload, String source) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!properties.isConfigured()) {
            logger.debug("Notification bridge disabled because URL or secret is missing");
            return;
        }
        pushPayload(new RealtimeEventBridgePayload(userId, new BridgeEvent("JOB_UPDATED", eventPayload), source), source);
    }

    private void pushPayload(Object bridgePayload, String source) {
        try {
            String payload = objectMapper.writeValueAsString(bridgePayload);
            String timestamp = Long.toString(Instant.now().toEpochMilli());
            String signature = signPayload(timestamp, payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getUrl()))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Larex-Notification-Bridge-Timestamp", timestamp)
                    .header("X-Larex-Notification-Bridge-Signature", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                logger.warn("Notification bridge rejected push from source {} with status {}", source, response.statusCode());
            }
        } catch (JacksonException error) {
            logger.warn("Failed to serialize notification bridge payload from source {}", source, error);
        } catch (IllegalStateException error) {
            logger.warn("Failed to sign notification bridge payload from source {}", source, error);
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Failed to push notification to Nuxt bridge from source {}", source, error);
        }
    }

    private String signPayload(String timestamp, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to sign notification bridge payload", error);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            builder.append(String.format("%02x", current));
        }
        return builder.toString();
    }

    private record NotificationBridgePayload(
            String userId,
            Notification notification,
            String source
    ) {}

    private record ActionEventBridgePayload(
            BridgeEvent event,
            String source
    ) {}

    private record RealtimeEventBridgePayload(
            String userId,
            BridgeEvent event,
            String source
    ) {}

    private record BridgeEvent(
            String type,
            Map<String, Object> payload
    ) {}
}
