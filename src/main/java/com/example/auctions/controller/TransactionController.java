package com.example.auctions.controller;

import com.example.auctions.model.Auction;
import com.example.auctions.model.User;
import com.example.auctions.service.AuctionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/transactions")
public class TransactionController {

    private final AuctionService auctionService;

    @Autowired
    public TransactionController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @GetMapping("/history")
    public String viewTransactionHistory(@AuthenticationPrincipal User user, Model model) {
        // Get auctions where user is seller
        List<Auction> soldAuctions = auctionService.getEndedAuctionsBySeller(user);
        
        // Get auctions where user is winner
        List<Auction> wonAuctions = auctionService.getAuctionsByWinner(user);
        
        model.addAttribute("soldAuctions", soldAuctions);
        model.addAttribute("wonAuctions", wonAuctions);
        
        return "transaction/history";
    }
} 