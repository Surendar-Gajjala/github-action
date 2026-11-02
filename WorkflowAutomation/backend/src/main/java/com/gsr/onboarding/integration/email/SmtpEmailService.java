package com.gsr.onboarding.integration.email;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.gsr.onboarding.config.AppProperties;
import com.gsr.onboarding.exception.IntegrationException;

/** Sends multipart (plain text + HTML) email over SMTP using the MAIL_* environment settings. */
@Component
@ConditionalOnProperty(name = "app.integrations.email", havingValue = "live")
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final AppProperties.Mail properties;

    public SmtpEmailService(JavaMailSender mailSender, AppProperties appProperties) {
        this.mailSender = mailSender;
        this.properties = appProperties.mail();
    }

    @Override
    public String send(EmailMessage message) {
        MimeMessage mime = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            if (properties.fromName() != null && !properties.fromName().isBlank()) {
                helper.setFrom(properties.from(), properties.fromName());
            } else {
                helper.setFrom(properties.from());
            }
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.textBody(), message.htmlBody());
            mime.setHeader("X-Onboarding-Reference", message.reference());
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new IntegrationException("Could not build the email message.", false, e);
        }
        try {
            mailSender.send(mime);
            String messageId = mime.getMessageID();
            log.info("Welcome email sent to {} (messageId={})", EmailMasking.mask(message.to()), messageId);
            return messageId;
        } catch (MailAuthenticationException e) {
            throw new IntegrationException("SMTP authentication failed. Check MAIL_USERNAME and MAIL_PASSWORD.", false, e);
        } catch (MailSendException e) {
            throw new IntegrationException("SMTP server rejected or could not deliver the message.", true, e);
        } catch (MailException e) {
            throw new IntegrationException("Email delivery failed: " + e.getClass().getSimpleName() + ".", true, e);
        } catch (MessagingException e) {
            throw new IntegrationException("Email was sent but the message ID could not be read.", false, e);
        }
    }
}
