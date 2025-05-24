package com.example.auctions.controller;

import com.example.auctions.model.Auction;
import com.example.auctions.model.Transaction;
import com.example.auctions.model.User;
import com.example.auctions.service.AuctionService;
import com.example.auctions.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/transactions")
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);
    
    private final AuctionService auctionService;
    private final TransactionService transactionService;

    @Autowired
    public TransactionController(
            AuctionService auctionService,
            TransactionService transactionService) {
        this.auctionService = auctionService;
        this.transactionService = transactionService;
    }

    @GetMapping("/history")
    public String viewTransactionHistory(@AuthenticationPrincipal User user, Model model) {
        // Get transactions where user is buyer
        List<Transaction> buyerTransactions = transactionService.getPurchaseHistory(user);
        
        // Get transactions where user is seller
        List<Transaction> sellerTransactions = transactionService.getSaleHistory(user);
        
        model.addAttribute("buyerTransactions", buyerTransactions);
        model.addAttribute("sellerTransactions", sellerTransactions);
        
        return "transaction/history";
    }

    @GetMapping("/create/{auctionId}")
    public String showTransactionConfirmation(
            @PathVariable Long auctionId,
            @AuthenticationPrincipal User user,
            Model model,
            RedirectAttributes redirectAttributes) {
        logger.info("Attempting to show transaction confirmation for auction {} by user {}", auctionId, user.getEmail());
        
        Auction auction = auctionService.getAuctionById(auctionId);
        
        if (auction == null) {
            logger.error("Auction {} not found", auctionId);
            redirectAttributes.addFlashAttribute("error", "Auction not found");
            return "redirect:/auctions/browse";
        }

        if (auction.getWinner() == null) {
            logger.error("Auction {} has no winner", auctionId);
            redirectAttributes.addFlashAttribute("error", "This auction has no winner yet");
            return "redirect:/auctions/" + auctionId;
        }

        if (!auction.getWinner().getId().equals(user.getId())) {
            logger.error("User {} is not the winner of auction {}", user.getEmail(), auctionId);
            redirectAttributes.addFlashAttribute("error", "You are not authorized to make payment for this auction");
            return "redirect:/auctions/" + auctionId;
        }

        model.addAttribute("auction", auction);
        return "transaction/confirm";
    }

    @PostMapping("/create/{auctionId}")
    public String createTransaction(
            @PathVariable Long auctionId, 
            @AuthenticationPrincipal User user,
            RedirectAttributes redirectAttributes) {
        logger.info("Attempting to create transaction for auction {} by user {}", auctionId, user.getEmail());
        
        try {
            Auction auction = auctionService.getAuctionById(auctionId);
            
            if (auction == null) {
                logger.error("Auction {} not found", auctionId);
                redirectAttributes.addFlashAttribute("error", "Auction not found");
                return "redirect:/auctions/browse";
            }

            if (auction.getWinner() == null) {
                logger.error("Auction {} has no winner", auctionId);
                redirectAttributes.addFlashAttribute("error", "This auction has no winner yet");
                return "redirect:/auctions/" + auctionId;
            }

            if (!auction.getWinner().getId().equals(user.getId())) {
                logger.error("User {} is not the winner of auction {}", user.getEmail(), auctionId);
                redirectAttributes.addFlashAttribute("error", "You are not authorized to make payment for this auction");
                return "redirect:/auctions/" + auctionId;
            }

            Transaction transaction = transactionService.createTransaction(auction);
            String paymentUrl = transactionService.initiatePayment(transaction);
            
            logger.info("Successfully created transaction and initiated payment for auction {}", auctionId);
            return "redirect:" + paymentUrl;
        } catch (Exception e) {
            logger.error("Error creating transaction for auction {}: {}", auctionId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to create transaction: " + e.getMessage());
            return "redirect:/auctions/" + auctionId;
        }
    }

    @GetMapping("/payment/callback")
    public String paymentCallback(@RequestParam Map<String, String> response, Model model) {
        logger.info("Received payment callback with response: {}", response);
        
        boolean success = transactionService.processPaymentCallback(response);
        
        if (success) {
            model.addAttribute("message", "Payment successful!");
            model.addAttribute("status", "success");
        } else {
            model.addAttribute("message", "Payment failed!");
            model.addAttribute("status", "error");
        }
        
        return "transaction/payment-result";
    }

    @GetMapping("/payment/{transactionId}")
    public String initiatePayment(
            @PathVariable Long transactionId, 
            @AuthenticationPrincipal User user,
            RedirectAttributes redirectAttributes) {
        logger.info("Attempting to initiate payment for transaction {} by user {}", transactionId, user.getEmail());
        
        try {
            Transaction transaction = transactionService.findById(transactionId);
            
            if (transaction == null) {
                logger.error("Transaction {} not found", transactionId);
                redirectAttributes.addFlashAttribute("error", "Transaction not found");
                return "redirect:/transactions/history";
            }
            
            if (!transaction.getBuyer().equals(user)) {
                logger.error("User {} is not authorized to pay for transaction {}", user.getEmail(), transactionId);
                redirectAttributes.addFlashAttribute("error", "You are not authorized to make this payment");
                return "redirect:/transactions/history";
            }
            
            String paymentUrl = transactionService.initiatePayment(transaction);
            logger.info("Successfully initiated payment for transaction {}", transactionId);
            return "redirect:" + paymentUrl;
        } catch (Exception e) {
            logger.error("Error initiating payment for transaction {}: {}", transactionId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Failed to initiate payment: " + e.getMessage());
            return "redirect:/transactions/history";
        }
    }
} 