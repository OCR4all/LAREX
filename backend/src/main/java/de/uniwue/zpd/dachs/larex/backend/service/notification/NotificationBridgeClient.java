package de.uniwue.zpd.dachs.larex.backend.service.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.entity.Notification;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationBridgeClient {

    private static final Logger logger = LoggerFactory.getLogger(NotificationBridgeClient.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${larex.notifications.bridge.enabled:true}")
    private boolean enabled;

    @Value("${larex.notifications.bridge.url:http://frontend:3000/api/notifications/broadcast}")
    private String bridgeUrl;

    @Value("${larex.notifications.bridge.secret:larex-notification-bridge-dev-secret}")
    private String bridgeSecret;

    public NotificationBridgeClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public void pushNotification(Notification notification, String source) {
        if (!enabled || notification == null) {
            return;
        }
        if (bridgeUrl == null || bridgeUrl.isBlank() || bridgeSecret == null || bridgeSecret.isBlank()) {
            logger.debug("Notification bridge disabled because URL or secret is missing");
            return;
        }

        try {
            String payload = objectMapper.writeValueAsString(new NotificationBridgePayload(
                    notification.getUserId(),
                    notification,
                    source
            ));
            String timestamp = Long.toString(Instant.now().toEpochMilli());
            String signature = signPayload(timestamp, payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(bridgeUrl))
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
        } catch (JsonProcessingException error) {
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
            mac.init(new SecretKeySpec(bridgeSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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
}
