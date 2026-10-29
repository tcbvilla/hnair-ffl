package com.ffl.service;

import com.ffl.entity.User;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final com.ffl.repository.UserRepository userRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> get(User user) {
        return toMap(user);
    }

    @Transactional
    public Map<String, Object> update(User user, String host, Integer port, String username,
                                      String password, Boolean ssl, String recipient) {
        String smtpHost = host == null ? "" : host.trim();
        String account = username == null ? "" : username.trim();
        String to = recipient == null ? "" : recipient.trim();
        if (smtpHost.isBlank() || smtpHost.length() > 200) {
            throw new IllegalArgumentException("请填写邮箱服务器");
        }
        if (port == null || port < 1 || port > 65535) {
            throw new IllegalArgumentException("端口不正确");
        }
        if (account.isBlank() || !account.contains("@") || account.length() > 200) {
            throw new IllegalArgumentException("请填写发信账号");
        }
        if (to.isBlank() || !to.contains("@") || to.length() > 200) {
            throw new IllegalArgumentException("请填写接收邮箱");
        }
        String secret = password == null ? "" : password;
        if (secret.isBlank()) {
            if (user.getSmtpPassword() == null || user.getSmtpPassword().isBlank()) {
                throw new IllegalArgumentException("请填写邮箱密码或授权码");
            }
            secret = user.getSmtpPassword();
        }
        user.setSmtpHost(smtpHost);
        user.setSmtpPort(port);
        user.setSmtpUsername(account);
        user.setSmtpPassword(secret);
        user.setSmtpSsl(Boolean.TRUE.equals(ssl));
        user.setSmtpTo(to);
        return toMap(userRepository.save(user));
    }

    public boolean configured(User user) {
        return user.getSmtpHost() != null && !user.getSmtpHost().isBlank()
                && user.getSmtpPort() != null
                && user.getSmtpUsername() != null && !user.getSmtpUsername().isBlank()
                && user.getSmtpPassword() != null && !user.getSmtpPassword().isBlank()
                && user.getSmtpTo() != null && !user.getSmtpTo().isBlank();
    }

    public void send(User user, String subject, String text) {
        if (!configured(user)) {
            throw new IllegalStateException("smtp is not configured");
        }
        boolean ssl = Boolean.TRUE.equals(user.getSmtpSsl());
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", user.getSmtpHost());
        props.put("mail.smtp.port", String.valueOf(user.getSmtpPort()));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        props.put("mail.smtp.ssl.trust", user.getSmtpHost());
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        if (ssl) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(user.getSmtpUsername(), user.getSmtpPassword());
            }
        });
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(user.getSmtpUsername()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(user.getSmtpTo()));
            message.setSubject(subject, "UTF-8");
            message.setText(text, "UTF-8");
            Transport.send(message);
            log.info("Sent monitor mail to user {}", user.getId());
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            log.warn("Failed to send monitor mail to user {}: {}", user.getId(), root.toString());
            throw new IllegalStateException(failureText(root));
        }
    }

    private static String failureText(Throwable root) {
        if (root instanceof java.net.SocketTimeoutException) {
            return "连接超时";
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return "发送失败";
        }
        return message.length() > 80 ? message.substring(0, 80) : message;
    }

    private Map<String, Object> toMap(User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("host", user.getSmtpHost());
        data.put("port", user.getSmtpPort());
        data.put("username", user.getSmtpUsername());
        data.put("recipient", user.getSmtpTo());
        data.put("ssl", Boolean.TRUE.equals(user.getSmtpSsl()));
        data.put("configured", configured(user));
        return data;
    }
}
