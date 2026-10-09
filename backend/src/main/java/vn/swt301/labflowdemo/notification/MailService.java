package vn.swt301.labflowdemo.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends email through MailTrap when {@code app.mail.enabled=true}; otherwise prints it to the log
 * (enough for local runs and tests - the link is in the console).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final ObjectProvider<JavaMailSender> mailSender;

    @Value("${app.mail.enabled}")
    private boolean enabled;

    @Value("${app.mail.from}")
    private String from;

    public void send(String to, String subject, String body) {
        if (!enabled) {
            log.info("Email (not sent, app.mail.enabled=false) to={} subject={}\n{}", to, subject, body);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.getObject().send(message);
        log.info("Email sent to={} subject={}", to, subject);
    }
}
