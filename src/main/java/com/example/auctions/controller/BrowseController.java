package com.example.auctions.controller;

import com.example.auctions.model.Auction;
import com.example.auctions.model.AuctionStatus;
import com.example.auctions.model.User;
import com.example.auctions.service.AuctionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/auctions/browse")
public class BrowseController {
    private final AuctionService auctionService;

    @Autowired
    public BrowseController(AuctionService auctionService) {
        this.auctionService = auctionService;
    }

    @GetMapping
    public String browseAuctions(
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal User user,
            Model model) {
        final int PAGE_SIZE = 12;
        Page<Auction> auctions = auctionService.getActiveAuctions(page, PAGE_SIZE);
        
        model.addAttribute("auctions", auctions.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", auctions.getTotalPages());
        
        return "auctions/browse";
    }
} 