package com.example.auctions.service;

import com.example.auctions.model.Auction;
import com.example.auctions.model.Transaction;
import com.example.auctions.model.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Autowired
    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    public void sendAuctionWonEmail(Auction auction) throws MessagingException {
        try {
            logger.info("Preparing to send auction won email for auction ID: {}", auction.getId());
            
            Context context = new Context();
            context.setVariable("auction", auction);
            context.setVariable("winner", auction.getWinner());
            context.setVariable("seller", auction.getSeller());
            context.setVariable("baseUrl", "http://localhost:8080");

            String emailContent = templateEngine.process("email/auction-won", context);
            logger.debug("Email content generated successfully");

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            String recipientEmail = auction.getWinner().getEmail();
            helper.setTo(recipientEmail);
            helper.setSubject("Congratulations! You've won the auction: " + auction.getProductName());
            helper.setText(emailContent, true);

            logger.info("Sending email to winner: {}", recipientEmail);
            mailSender.send(message);
            logger.info("Email sent successfully to: {}", recipientEmail);
            
        } catch (Exception e) {
            logger.error("Failed to send auction won email for auction ID: " + auction.getId(), e);
            throw e;
        }
    }

    public void sendInvoiceEmail(Transaction transaction, Auction auction) {
        try {
            logger.info("Preparing to send invoice email for transaction ID: {}", transaction.getId());
            
            Context context = new Context();
            context.setVariable("transaction", transaction);
            context.setVariable("auction", auction);
            
            String emailContent = templateEngine.process("email/invoice", context);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(transaction.getBuyer().getEmail());
            helper.setSubject("Invoice for Your Auction Purchase");
            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("Successfully sent invoice email for transaction ID: {}", transaction.getId());
            
        } catch (MessagingException e) {
            logger.error("Failed to send invoice email for transaction ID: {}", transaction.getId(), e);
            throw new RuntimeException("Failed to send invoice email", e);
        }
    }

    public void sendPaymentNotificationToSeller(Transaction transaction, Auction auction) {
        try {
            logger.info("Preparing to send payment notification email to seller for transaction ID: {}", transaction.getId());
            logger.info("Seller email: {}", transaction.getSeller().getEmail());
            
            Context context = new Context();
            context.setVariable("transaction", transaction);
            context.setVariable("auction", auction);
            context.setVariable("buyer", transaction.getBuyer());
            
            logger.info("Processing email template");
            String emailContent = templateEngine.process("email/payment-notification", context);
            logger.info("Email template processed successfully");

            logger.info("Creating email message");
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setTo(transaction.getSeller().getEmail());
            helper.setSubject("Payment Received for Your Auction Item");
            helper.setText(emailContent, true);
            logger.info("Email message created successfully");

            logger.info("Sending email to seller");
            mailSender.send(message);
            logger.info("Successfully sent payment notification email to seller for transaction ID: {}", transaction.getId());
            
        } catch (MessagingException e) {
            logger.error("Failed to send payment notification email to seller for transaction ID: {}", transaction.getId());
            logger.error("Error details: {}", e.getMessage());
            logger.error("Full stack trace:", e);
            throw new RuntimeException("Failed to send payment notification email", e);
        }
    }
} 