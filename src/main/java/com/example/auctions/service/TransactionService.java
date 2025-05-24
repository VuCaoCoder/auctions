package com.example.auctions.service;

import com.example.auctions.model.Transaction;
import com.example.auctions.model.TransactionStatus;
import com.example.auctions.model.User;
import com.example.auctions.model.Auction;
import com.example.auctions.repository.TransactionRepository;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TransactionService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);
    
    private final TransactionRepository transactionRepository;
    private final EmailService emailService;
    private final PaymentService paymentService;
    private final AuctionService auctionService;

    @Autowired
    public TransactionService(
            TransactionRepository transactionRepository,
            EmailService emailService,
            PaymentService paymentService,
            AuctionService auctionService) {
        this.transactionRepository = transactionRepository;
        this.emailService = emailService;
        this.paymentService = paymentService;
        this.auctionService = auctionService;
    }

    public List<Transaction> getPurchaseHistory(User buyer) {
        return transactionRepository.findByBuyerOrderByTransactionDateDesc(buyer);
    }

    public List<Transaction> getSaleHistory(User seller) {
        return transactionRepository.findBySellerOrderByTransactionDateDesc(seller);
    }

    public Transaction findById(Long id) {
        return transactionRepository.findByIdWithUsers(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found with id: " + id));
    }

    @Transactional
    public Transaction createTransaction(Auction auction) {
        logger.info("Creating transaction for auction: {}", auction.getId());
        
        Transaction transaction = new Transaction();
        transaction.setBuyer(auction.getWinner());
        transaction.setSeller(auction.getSeller());
        transaction.setItemName(auction.getProductName());
        transaction.setItemDescription(auction.getDescription());
        transaction.setPrice(auction.getCurrentPrice());
        transaction.setTransactionDate(LocalDateTime.now());
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setAuctionId(auction.getId());

        transaction = transactionRepository.save(transaction);
        logger.info("Transaction created with ID: {}", transaction.getId());

        return transaction;
    }

    public String initiatePayment(Transaction transaction) {
        return paymentService.createPaymentUrl(transaction);
    }

    @Transactional
    public boolean processPaymentCallback(Map<String, String> response) {
        logger.info("Processing payment callback with response: {}", response);
        
        String transactionId = response.get("vnp_TxnRef");
        if (transactionId == null) {
            logger.error("No transaction ID found in response");
            return false;
        }
        
        logger.info("Looking up transaction with ID: {}", transactionId);
        Transaction transaction = findById(Long.parseLong(transactionId));
        logger.info("Found transaction: {}", transaction);
        logger.info("Seller information: {}", transaction.getSeller());
        logger.info("Seller email: {}", transaction.getSeller().getEmail());
        
        logger.info("Processing payment callback through PaymentService");
        boolean success = paymentService.processPaymentCallback(response);
        logger.info("Payment processing result: {}", success);
        
        if (success) {
            transaction.setStatus(TransactionStatus.COMPLETED);
            logger.info("Payment successful for transaction {}", transactionId);
            
            try {
                logger.info("Fetching auction details for transaction");
                Auction auction = auctionService.getAuctionById(transaction.getAuctionId());
                logger.info("Found auction: {}", auction);
                logger.info("Auction seller: {}", auction.getSeller());
                
                // Send invoice email to buyer
                logger.info("Sending invoice email to buyer: {}", transaction.getBuyer().getEmail());
                emailService.sendInvoiceEmail(transaction, auction);
                logger.info("Invoice email sent to buyer successfully");
                
                // Send notification email to seller
                logger.info("Sending payment notification email to seller: {}", transaction.getSeller().getEmail());
                emailService.sendPaymentNotificationToSeller(transaction, auction);
                logger.info("Payment notification email sent to seller successfully");
            } catch (Exception e) {
                logger.error("Failed to send emails for transaction {}: {}", transactionId, e.getMessage());
                logger.error("Full stack trace:", e);
            }
        } else {
            transaction.setStatus(TransactionStatus.FAILED);
            logger.error("Payment failed for transaction {}", transactionId);
        }
        
        logger.info("Saving transaction with status: {}", transaction.getStatus());
        saveTransaction(transaction);
        return success;
    }

    public Transaction saveTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }
} 