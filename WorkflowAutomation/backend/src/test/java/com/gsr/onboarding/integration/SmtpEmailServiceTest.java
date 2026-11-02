package com.gsr.onboarding.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import com.gsr.onboarding.TestFixtures;
import com.gsr.onboarding.exception.IntegrationException;
import com.gsr.onboarding.integration.email.EmailMessage;
import com.gsr.onboarding.integration.email.SmtpEmailService;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;

class SmtpEmailServiceTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP);

    private SmtpEmailService service(int port) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("localhost");
        sender.setPort(port);
        return new SmtpEmailService(sender, TestFixtures.properties(false, true));
    }

    @Test
    void sendsMultipartEmail() throws Exception {
        String messageId = service(greenMail.getSmtp().getPort()).send(new EmailMessage("priya.sharma@example.com",
                "Welcome to Acme Corp, Priya!", "Hi Priya (text)", "<p>Hi Priya (html)</p>", "onboarding-workflow-1"));

        MimeMessage[] received = greenMail.getReceivedMessages();
        assertThat(received).hasSize(1);
        assertThat(received[0].getSubject()).isEqualTo("Welcome to Acme Corp, Priya!");
        assertThat(received[0].getHeader("X-Onboarding-Reference")[0]).isEqualTo("onboarding-workflow-1");
        String raw = GreenMailUtil.getWholeMessage(received[0]);
        assertThat(raw).contains("Hi Priya (text)").contains("Hi Priya (html)");
        assertThat(messageId).isNotBlank();
    }

    @Test
    void unreachableServerBecomesIntegrationException() {
        assertThatThrownBy(() -> service(1).send(new EmailMessage("a@example.com", "s", "t", "<p>h</p>", "r")))
                .isInstanceOf(IntegrationException.class);
    }

    @Test
    void toStringNeverExposesBodies() {
        EmailMessage message = new EmailMessage("priya.sharma@example.com", "Subject", "secret text", "secret html", "r");
        assertThat(message.toString()).doesNotContain("secret").doesNotContain("priya.sharma").contains("p***@example.com");
    }
}
