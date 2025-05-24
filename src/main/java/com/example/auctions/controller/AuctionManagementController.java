package com.example.auctions.controller;

import com.example.auctions.model.Auction;
import com.example.auctions.model.AuctionStatus;
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
@RequestMapping("/auctions/management")
public class AuctionManagementController {
    
    private final AuctionService auctionService;
    
    @Autowired
    public AuctionManagementController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }
    
    @GetMapping("/bids")
    public String viewAuctionBids(@AuthenticationPrincipal User user, Model model) {
        List<Auction> activeAuctions = auctionService.getAuctionsBySellerAndStatus(user, AuctionStatus.ACTIVE);
        
        // Calculate unique bidders for each auction
        activeAuctions.forEach(auction -> {
            long uniqueBidders = auction.getBids().stream()
                .map(bid -> bid.getBidder().getId())
                .distinct()
                .count();
            auction.setUniqueBidders(uniqueBidders);
        });
        
        model.addAttribute("auctions", activeAuctions);
        return "auctions/auction-bids-management";
    }
} 