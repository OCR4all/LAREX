package de.uniwue.zpd.dachs.larex.backend.service.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uniwue.zpd.dachs.larex.backend.entity.Notification;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(bridgeUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("X-Larex-Notification-Bridge-Secret", bridgeSecret)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 400) {
                logger.warn("Notification bridge rejected push with status {}", response.statusCode());
            }
        } catch (JsonProcessingException error) {
            logger.warn("Failed to serialize notification bridge payload", error);
        } catch (IOException | InterruptedException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Failed to push notification to Nuxt bridge", error);
        }
    }

    private record NotificationBridgePayload(
            String userId,
            Notification notification,
            String source
    ) {}
}
