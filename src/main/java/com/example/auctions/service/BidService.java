package com.example.auctions.service;

import com.example.auctions.model.Auction;
import com.example.auctions.model.AuctionStatus;
import com.example.auctions.model.Bid;
import com.example.auctions.model.User;
import com.example.auctions.repository.AuctionRepository;
import com.example.auctions.repository.BidRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BidService {
    private final BidRepository bidRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AuctionRepository auctionRepository;
    private final AuctionService auctionService;

    @Autowired
    public BidService(BidRepository bidRepository, SimpMessagingTemplate messagingTemplate, AuctionRepository auctionRepository, AuctionService auctionService) {
        this.bidRepository = bidRepository;
        this.messagingTemplate = messagingTemplate;
        this.auctionRepository = auctionRepository;
        this.auctionService = auctionService;
    }

    @Transactional
    public Bid placeBid(Auction auction, User bidder, BigDecimal amount) {
        // Get current highest bid or starting price
        BigDecimal currentPrice = auction.getCurrentPrice() != null ? 
            auction.getCurrentPrice() : auction.getStartingPrice();

        // Validate bid amount
        if (amount.compareTo(currentPrice) <= 0) {
            throw new RuntimeException("Bid amount must be higher than current price: $" + currentPrice);
        }

        // Create and save new bid
        Bid bid = new Bid();
        bid.setAuction(auction);
        bid.setBidder(bidder);
        bid.setAmount(amount);
        bid.setBidTime(LocalDateTime.now());
        
        // Save the bid first
        Bid savedBid = bidRepository.save(bid);
        
        // Update auction's current price
        auction.setCurrentPrice(amount);
        auctionRepository.save(auction);

        // Notify about new bid
        notifyNewBid(auction.getId(), amount, bidder.getFullName());

        return savedBid;
    }

    public List<Bid> getAuctionBids(Auction auction) {
        return bidRepository.findByAuctionOrderByAmountDesc(auction);
    }

    public List<Bid> getUserBids(Long userId) {
        return bidRepository.findByBidderIdOrderByBidTimeDesc(userId);
    }

    public List<Bid> getActiveBids(Long userId) {
        return bidRepository.findByBidderIdAndAuctionStatusOrderByBidTimeDesc(userId, AuctionStatus.ACTIVE);
    }

    public Optional<Bid> getHighestBid(Auction auction) {
        return bidRepository.findTopByAuctionOrderByAmountDesc(auction);
    }

    public List<Auction> getWonAuctions(Long userId) {
        return bidRepository.findWonAuctionsByBidderId(userId);
    }

    private void notifyNewBid(Long auctionId, BigDecimal amount, String bidderName) {
        String destination = "/topic/auction/" + auctionId;
        BidMessage message = new BidMessage(amount, bidderName);
        messagingTemplate.convertAndSend(destination, message);
    }

    // Inner class for WebSocket messages
    private static class BidMessage {
        private final BigDecimal amount;
        private final String bidderName;
        private final LocalDateTime timestamp;

        public BidMessage(BigDecimal amount, String bidderName) {
            this.amount = amount;
            this.bidderName = bidderName;
            this.timestamp = LocalDateTime.now();
        }

        @JsonProperty("amount")
        public BigDecimal getAmount() {
            return amount;
        }

        @JsonProperty("bidderName")
        public String getBidderName() {
            return bidderName;
        }

        @JsonProperty("timestamp")
        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}