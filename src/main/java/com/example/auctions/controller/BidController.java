package com.example.auctions.controller;

import com.example.auctions.model.Auction;
import com.example.auctions.model.AuctionStatus;
import com.example.auctions.model.Bid;
import com.example.auctions.model.User;
import com.example.auctions.service.AuctionService;
import com.example.auctions.service.BidService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/bids")
public class BidController {
    private final BidService bidService;
    private final AuctionService auctionService;
    private final SimpMessagingTemplate template;

    @Autowired
    public BidController(BidService bidService, AuctionService auctionService, SimpMessagingTemplate template) {
        this.bidService = bidService;
        this.auctionService = auctionService;
        this.template = template;
    }

    @GetMapping("/auction/{auctionId}")
    public String viewAuctionBids(@PathVariable Long auctionId, @AuthenticationPrincipal User user, Model model) {
        Auction auction = auctionService.getAuctionById(auctionId);
        
        // Check if auction is active
        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            return "redirect:/auctions/" + auctionId;
        }
        
        // Check if user is not the seller
        if (auction.getSeller().getId().equals(user.getId())) {
            return "redirect:/auctions/" + auctionId;
        }
        
        List<Bid> bids = bidService.getAuctionBids(auction);
        model.addAttribute("auction", auction);
        model.addAttribute("bids", bids);
        return "bids/auction-bids";
    }

    @PostMapping("/place")
    @ResponseBody
    public ResponseEntity<?> placeBid(
            @AuthenticationPrincipal User user,
            @RequestParam Long auctionId,
            @RequestParam BigDecimal amount) {
        try {
            Auction auction = auctionService.getAuctionById(auctionId);
            
            // Get current highest bid or starting price
            BigDecimal currentHighestBid = auction.getCurrentPrice() != null ? 
                auction.getCurrentPrice() : auction.getStartingPrice();

            // Validate bid amount - must be strictly higher than current price
            if (amount.compareTo(currentHighestBid) <= 0) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Bid amount must be higher than $" + currentHighestBid
                ));
            }

            // Check if auction is active
            if (auction.getStatus() != AuctionStatus.ACTIVE) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "This auction is not active"
                ));
            }

            // Check if user is not the owner
            if (auction.getSeller().getId().equals(user.getId())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "You cannot bid on your own auction"
                ));
            }

            Bid bid = bidService.placeBid(auction, user, amount);
            
            // Send WebSocket message
            template.convertAndSend("/topic/auction/" + auctionId, Map.of(
                "amount", bid.getAmount(),
                "bidderName", bid.getBidder().getFullName(),
                "timestamp", bid.getBidTime()
            ));

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Bid placed successfully",
                "newAmount", bid.getAmount()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

    @MessageMapping("/bid")
    @SendTo("/topic/bids")
    public Bid bid(Bid bid) {
        return bid;
    }

    @GetMapping("/my-bids")
    public String viewMyBids(@AuthenticationPrincipal User user, Model model) {
        List<Bid> bids = bidService.getUserBids(user.getId());
        model.addAttribute("bids", bids);
        return "bids/my-bids";
    }

    @GetMapping("/won")
    public String viewWonAuctions(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        Page<Auction> wonAuctionsPage = bidService.getWonAuctionsWithPagination(user.getId(), page, size);
        model.addAttribute("auctions", wonAuctionsPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", wonAuctionsPage.getTotalPages());
        return "bids/won-auctions";
    }

    @GetMapping("/active")
    public String viewActiveBids(@AuthenticationPrincipal User user, Model model) {
        List<Bid> activeBids = bidService.getActiveBids(user.getId());
        model.addAttribute("bids", activeBids);
        return "bids/active-bids";
    }
} 