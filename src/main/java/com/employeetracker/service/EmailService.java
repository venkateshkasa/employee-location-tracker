package com.employeetracker.service;

import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails (currently just the "welcome" email sent to a
 * newly created employee).
 * <p>
 * Every public method here swallows its own exceptions and simply logs +
 * returns false on failure, so a broken/unconfigured mail server can never
 * cause employee creation itself to fail or roll back. The full stack trace
 * is always logged on failure so the exact SMTP/auth error is visible in the
 * console instead of being silently swallowed.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String mailHost;
    private final int mailPort;
    private final String mailUsername;
    private final boolean mailPasswordConfigured;

    public EmailService(JavaMailSender mailSender,
                         @Value("${app.mail.from:}") String fromAddress,
                         @Value("${spring.mail.host:}") String mailHost,
                         @Value("${spring.mail.port:0}") int mailPort,
                         @Value("${spring.mail.username:}") String mailUsername,
                         @Value("${spring.mail.password:}") String mailPassword) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.mailHost = mailHost;
        this.mailPort = mailPort;
        this.mailUsername = mailUsername;
        this.mailPasswordConfigured = mailPassword != null && !mailPassword.isBlank();
    }

    /**
     * Logs, once at startup, whether Gmail SMTP looks configured at all.
     * This is what lets you immediately see - without waiting for the first
     * employee to be created - whether MAIL_USERNAME / MAIL_PASSWORD were
     * actually picked up from the environment.
     */
    @PostConstruct
    public void logMailConfigurationOnStartup() {
        boolean usernameConfigured = mailUsername != null && !mailUsername.isBlank();

        log.info("Mail configuration check -> host={}, port={}, username={}, passwordConfigured={}",
                mailHost,
                mailPort,
                usernameConfigured ? mailUsername : "(not set)",
                mailPasswordConfigured);

        if (!usernameConfigured || !mailPasswordConfigured) {
            log.warn("Gmail SMTP username/password is NOT fully configured (MAIL_USERNAME/MAIL_PASSWORD "
                    + "environment variables). Welcome emails WILL FAIL to authenticate until both are set. "
                    + "MAIL_PASSWORD must be a 16-character Google Account 'App Password' - a normal Gmail "
                    + "login password will be rejected by Gmail's SMTP server even if it is correct.");
        }
    }

    /**
     * Sends the "welcome" email to a newly created employee containing
     * their Employee Name, Employee ID, Username, and a "Create Password"
     * link that lets them set their own password (the password itself is
     * never included in this email).
     *
     * @param passwordSetupLink the full, clickable
     *                          {@code ${app.base-url}/setup-password?token=<token>}
     *                          URL for the employee to set their password
     * @return true if the email was handed off to the mail server
     *         successfully, false otherwise (never throws).
     */
    public boolean sendWelcomeEmail(String toEmail, String employeeName, String employeeId, String username,
                                     String passwordSetupLink) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Skipping welcome email for employee {} - no email address on file", employeeId);
            return false;
        }

        log.info("Sending welcome email to: {}", toEmail);

        if ((mailUsername == null || mailUsername.isBlank()) || !mailPasswordConfigured) {
            log.error("Cannot send welcome email to {} for employee {}: Gmail SMTP username/password is not "
                    + "configured. Set the MAIL_USERNAME (full Gmail address) and MAIL_PASSWORD (Google App "
                    + "Password, not your normal Gmail password) environment variables and restart the app.",
                    toEmail, employeeId);
            return false;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            // multipart=true so we can offer both an HTML body (with the clickable
            // "Create Password" button) and a plain-text fallback in the same email.
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            if (fromAddress != null && !fromAddress.isBlank()) {
                helper.setFrom(fromAddress);
            }
            helper.setTo(toEmail);
            helper.setSubject("Welcome to the Team, " + employeeName + "!");
            helper.setText(
                    buildWelcomeEmailPlainText(employeeName, employeeId, username, passwordSetupLink),
                    buildWelcomeEmailHtml(employeeName, employeeId, username, passwordSetupLink)
            );

            mailSender.send(mimeMessage);

            log.info("Welcome email sent successfully to {} for employee {} ({})", toEmail, employeeName, employeeId);
            return true;
        } catch (RuntimeException | jakarta.mail.MessagingException ex) {
            // Deliberately caught broadly (MailException, MailAuthenticationException,
            // MailSendException, MessagingException, etc.): SMTP/auth/network failures
            // must never bubble up and affect the employee-creation flow that
            // triggered this. The full stack trace is logged so the exact underlying
            // SMTP error (e.g. "535-5.7.8 Username and Password not accepted") is
            // visible instead of just a one-line message.
            log.error("Failed to send welcome email to {} for employee {}", toEmail, employeeId, ex);
            return false;
        }
    }

    private String buildWelcomeEmailPlainText(String employeeName, String employeeId, String username,
                                               String passwordSetupLink) {
        return "Hi " + employeeName + ",\n\n"
                + "Welcome to the team! We're excited to have you on board.\n\n"
                + "Your employee account has been created successfully with the following details:\n\n"
                + "Employee Name : " + employeeName + "\n"
                + "Employee ID   : " + employeeId + "\n"
                + "Username      : " + username + "\n\n"
                + "Before you can log in, please set your password using the secure link below. "
                + "This link will expire in 24 hours:\n\n"
                + passwordSetupLink + "\n\n"
                + "If you have any questions, please reach out to your administrator.\n\n"
                + "Regards,\n"
                + "HR / Admin Team";
    }

    private String buildWelcomeEmailHtml(String employeeName, String employeeId, String username,
                                          String passwordSetupLink) {
        return "<!DOCTYPE html>"
                + "<html><body style=\"margin:0;padding:0;background-color:#f4f6f8;font-family:Arial, Helvetica, sans-serif;\">"
                + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background-color:#f4f6f8;padding:24px 0;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"480\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background-color:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.08);\">"
                + "<tr><td style=\"background-color:#0d6efd;padding:20px 32px;\">"
                + "<h2 style=\"color:#ffffff;margin:0;font-size:20px;\">Welcome to the Team!</h2>"
                + "</td></tr>"
                + "<tr><td style=\"padding:28px 32px;color:#212529;\">"
                + "<p style=\"margin-top:0;\">Hi " + escapeHtml(employeeName) + ",</p>"
                + "<p>We're excited to have you on board. Your employee account has been created with the "
                + "following details:</p>"
                + "<table role=\"presentation\" cellpadding=\"6\" cellspacing=\"0\" style=\"margin:16px 0;\">"
                + "<tr><td style=\"color:#6c757d;\">Employee Name</td><td><strong>" + escapeHtml(employeeName) + "</strong></td></tr>"
                + "<tr><td style=\"color:#6c757d;\">Employee ID</td><td><strong>" + escapeHtml(employeeId) + "</strong></td></tr>"
                + "<tr><td style=\"color:#6c757d;\">Username</td><td><strong>" + escapeHtml(username) + "</strong></td></tr>"
                + "</table>"
                + "<p>Before you can log in, please create your password. For security, this link is "
                + "valid for <strong>24 hours</strong> and can only be used once.</p>"
                + "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:24px 0;\">"
                + "<tr><td align=\"center\" style=\"border-radius:6px;background-color:#0d6efd;\">"
                + "<a href=\"" + escapeHtmlAttribute(passwordSetupLink) + "\" target=\"_blank\" "
                + "style=\"display:inline-block;padding:12px 28px;color:#ffffff;text-decoration:none;"
                + "font-weight:bold;border-radius:6px;\">Create Password</a>"
                + "</td></tr>"
                + "</table>"
                + "<p style=\"font-size:13px;color:#6c757d;\">If the button above doesn't work, copy and paste "
                + "this link into your browser:<br>"
                + "<a href=\"" + escapeHtmlAttribute(passwordSetupLink) + "\">" + escapeHtml(passwordSetupLink) + "</a></p>"
                + "<p style=\"margin-bottom:0;\">If you have any questions, please reach out to your administrator.</p>"
                + "<p style=\"margin-bottom:0;\">Regards,<br>HR / Admin Team</p>"
                + "</td></tr>"
                + "</table>"
                + "</td></tr>"
                + "</table>"
                + "</body></html>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String escapeHtmlAttribute(String value) {
        return escapeHtml(value);
    }
}
