package de.uniwue.zpd.dachs.larex.backend.service.notification;

import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Notification;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Service for sending email notifications.
 * Uses Spring's JavaMailSender to send emails asynchronously.
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final NotificationPreferenceService preferenceService;
    private final UserService userService;

    @Value("${spring.mail.from:noreply@larex.localhost}")
    private String fromAddress;

    @Value("${spring.mail.enabled:true}")
    private boolean emailEnabled;

    @Value("${app.name:LAREX}")
    private String appName;

    public EmailService(JavaMailSender mailSender, 
                       NotificationPreferenceService preferenceService,
                       UserService userService) {
        this.mailSender = mailSender;
        this.preferenceService = preferenceService;
        this.userService = userService;
    }

    /**
     * Send notification email if user has email enabled for this notification type.
     * This method is async to not block the main thread.
     */
    @Async
    public void sendNotificationEmailIfEnabled(String userId, Notification notification) {
        if (!emailEnabled) {
            logger.debug("Email sending is disabled, skipping email for notification: {}", notification.getId());
            return;
        }

        // Check if user has email notifications enabled for this type
        if (!preferenceService.isEmailEnabledForType(userId, notification.getType())) {
            logger.debug("User {} has email disabled for notification type: {}", userId, notification.getType());
            return;
        }

        // Get user email from Keycloak
        String userEmail = getUserEmail(userId);
        if (userEmail == null || userEmail.isBlank()) {
            logger.warn("Cannot send email to user {}: no email address found", userId);
            return;
        }

        sendEmail(userEmail, notification);
    }

    /**
     * Send a simple email notification.
     */
    private void sendEmail(String toEmail, Notification notification) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject(formatSubject(notification));
            message.setText(formatBody(notification));

            mailSender.send(message);
            logger.info("Sent notification email to {} for notification type: {}", toEmail, notification.getType());
        } catch (MailException e) {
            logger.error("Failed to send email to {}: {}", toEmail, e.getMessage());
        }
    }

    /**
     * Format email subject based on notification.
     */
    private String formatSubject(Notification notification) {
        return String.format("[%s] %s", appName, notification.getTitle());
    }

    /**
     * Format email body based on notification.
     */
    private String formatBody(Notification notification) {
        StringBuilder body = new StringBuilder();
        body.append(notification.getMessage());
        body.append("\n\n");
        body.append("---\n");
        body.append("This is an automated notification from ").append(appName).append(".\n");
        body.append("You can manage your notification preferences in your account settings.");
        return body.toString();
    }

    /**
     * Get user's email address from Keycloak via UserService.
     */
    private String getUserEmail(String userId) {
        try {
            return userService.getUserById(userId)
                    .map(UserDto::email)
                    .orElse(null);
        } catch (Exception e) {
            logger.warn("Failed to get email for user {}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * Send a test email to verify configuration.
     */
    public boolean sendTestEmail(String toEmail) {
        if (!emailEnabled) {
            logger.warn("Email sending is disabled");
            return false;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject(String.format("[%s] Test Email", appName));
            message.setText("This is a test email from " + appName + " to verify your email configuration is working correctly.");

            mailSender.send(message);
            logger.info("Sent test email to {}", toEmail);
            return true;
        } catch (MailException e) {
            logger.error("Failed to send test email to {}: {}", toEmail, e.getMessage());
            return false;
        }
    }
}
