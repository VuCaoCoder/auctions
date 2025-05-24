package com.example.auctions.service;

import com.example.auctions.model.Auction;
import com.example.auctions.model.AuctionStatus;
import com.example.auctions.model.Bid;
import com.example.auctions.model.User;
import com.example.auctions.repository.AuctionRepository;
import com.example.auctions.repository.BidRepository;
import jakarta.annotation.PostConstruct;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

@Service
public class AuctionService {

    private static final Logger logger = LoggerFactory.getLogger(AuctionService.class);

    @Value("${auction.images.upload.path}")
    private String uploadPath;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private BidRepository bidRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private EmailService emailService;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(uploadPath));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory!", e);
        }
    }

    public List<Auction> getAuctionsBySeller(User seller) {
        return auctionRepository.findBySeller(seller, Pageable.unpaged()).getContent();
    }

    public List<Auction> getAuctionsBySellerAndStatus(User seller, AuctionStatus status) {
        return auctionRepository.findBySellerAndStatusOrderByEndTimeDesc(seller, status);
    }

    public Auction getAuctionById(Long id) {
        return auctionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Auction not found"));
    }

    public Page<Auction> getActiveAuctions(int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return auctionRepository.findByStatus(AuctionStatus.ACTIVE, pageRequest);
    }

    public Auction createAuction(Auction auction, String action) throws IOException {
        // Handle image upload
        if (auction.getImageFile() != null && !auction.getImageFile().isEmpty()) {
            String imageName = saveImage(auction.getImageFile());
            auction.setImage(imageName);
        }

        // Set status based on action
        if ("publish".equals(action)) {
            auction.setStatus(AuctionStatus.ACTIVE);
        } else {
            auction.setStatus(AuctionStatus.DRAFT);
        }

        return auctionRepository.save(auction);
    }

    public Auction updateAuction(Auction auction, String action) throws IOException {
        Auction existingAuction = getAuctionById(auction.getId());

        // Only allow updates if auction is in DRAFT status or hasn't started yet
        if (existingAuction.getStatus() != AuctionStatus.DRAFT && 
            existingAuction.getStatus() != AuctionStatus.ACTIVE && 
            LocalDateTime.now().isBefore(existingAuction.getEndTime())) {
            throw new RuntimeException("Cannot update auction that has already started or ended");
        }

        // Handle new image if provided
        if (auction.getImageFile() != null && !auction.getImageFile().isEmpty()) {
            // Delete old image if exists
            if (existingAuction.getImage() != null) {
                deleteImage(existingAuction.getImage());
            }
            // Save new image
            String imageName = saveImage(auction.getImageFile());
            auction.setImage(imageName);
        } else {
            auction.setImage(existingAuction.getImage());
        }

        // Handle status changes
        if ("publish".equals(action) && existingAuction.getStatus() == AuctionStatus.DRAFT) {
            auction.setStatus(AuctionStatus.ACTIVE);
        } else if ("delete".equals(action) && existingAuction.getStatus() == AuctionStatus.DRAFT) {
            deleteAuction(existingAuction);
            return null;
        } else {
            auction.setStatus(existingAuction.getStatus());
        }

        auction.setCreatedAt(existingAuction.getCreatedAt());
        auction.setSeller(existingAuction.getSeller());

        return auctionRepository.save(auction);
    }

    public void deleteAuction(Auction auction) {
        if (auction.getStatus() != AuctionStatus.DRAFT) {
            throw new RuntimeException("Can only delete draft auctions");
        }

        // Delete image file if exists
        if (auction.getImage() != null) {
            deleteImage(auction.getImage());
        }

        // Delete auction from database
        auctionRepository.delete(auction);
    }

    private String saveImage(MultipartFile image) throws IOException {
        // Generate unique filename with original extension
        String originalFilename = image.getOriginalFilename();
        String extension = originalFilename != null ? originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
        String filename = UUID.randomUUID().toString() + extension;
        
        // Create full path
        Path destinationPath = Paths.get(uploadPath).resolve(filename);
        
        // Ensure directory exists
        Files.createDirectories(destinationPath.getParent());
        
        // Copy file to destination
        Files.copy(image.getInputStream(), destinationPath, StandardCopyOption.REPLACE_EXISTING);
        
        return filename;
    }

    private void deleteImage(String imageName) {
        try {
            Path imagePath = Paths.get(uploadPath).resolve(imageName);
            Files.deleteIfExists(imagePath);
        } catch (IOException e) {
            // Log error but don't throw exception
            e.printStackTrace();
        }
    }

    @Scheduled(fixedRate = 60000) // Run every minute
    @Transactional
    public void updateExpiredAuctions() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Auction> activeAuctions = auctionRepository.findByStatusAndEndTimeBefore(AuctionStatus.ACTIVE, now);
            
            for (Auction auction : activeAuctions) {
                endAuction(auction);
            }
        } catch (Exception e) {
            System.err.println("Error updating expired auctions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Transactional
    public void endAuction(Auction auction) {
        logger.info("Starting to end auction ID: {}", auction.getId());
        
        // Find the highest bid
        Optional<Bid> highestBid = bidRepository.findTopByAuctionOrderByAmountDesc(auction);
        logger.debug("Highest bid found: {}", highestBid.isPresent() ? highestBid.get().getAmount() : "No bids");
        
        // Update auction status and winner
        auction.setStatus(AuctionStatus.ENDED);
        final Auction finalAuction = auction;
        highestBid.ifPresent(bid -> {
            finalAuction.setWinner(bid.getBidder());
            logger.info("Setting winner for auction {}: {}", finalAuction.getId(), bid.getBidder().getEmail());
        });
        
        // Save the auction
        auction = auctionRepository.save(finalAuction);
        logger.debug("Auction saved with status ENDED");
        
        // Send email notification to winner if exists
        if (auction.getWinner() != null) {
            try {
                logger.info("Attempting to send winner notification email");
                emailService.sendAuctionWonEmail(auction);
                logger.info("Winner notification email sent successfully");
            } catch (MessagingException e) {
                // Log error but don't stop the process
                logger.error("Failed to send auction won email: " + e.getMessage(), e);
            }
        } else {
            logger.info("No winner to notify for auction ID: {}", auction.getId());
        }
        
        // Notify participants about auction end
        String destination = "/topic/auction/" + auction.getId();
        AuctionEndMessage message = new AuctionEndMessage(
            auction.getId(),
            highestBid.map(Bid::getAmount).orElse(auction.getStartingPrice()),
            highestBid.map(bid -> bid.getBidder().getFullName()).orElse(null)
        );
        messagingTemplate.convertAndSend(destination, message);
        logger.debug("WebSocket notification sent for auction end");
        
        logger.info("Auction ID: {} ended successfully", auction.getId());
    }

    public Page<Auction> getAuctionsBySeller(User seller, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return auctionRepository.findBySeller(seller, pageRequest);
    }

    public Page<Auction> getAuctionsBySellerAndStatus(User seller, AuctionStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return auctionRepository.findBySellerAndStatus(seller, status, pageRequest);
    }

    public List<Auction> getEndedAuctionsBySeller(User seller) {
        return auctionRepository.findBySellerAndStatusOrderByEndTimeDesc(seller, AuctionStatus.ENDED);
    }

    public List<Auction> getAuctionsByWinner(User winner) {
        return auctionRepository.findByWinner(winner);
    }

    // Inner class for WebSocket messages
    private static class AuctionEndMessage {
        private final Long auctionId;
        private final java.math.BigDecimal finalPrice;
        private final String winnerName;
        private final LocalDateTime endTime;

        public AuctionEndMessage(Long auctionId, java.math.BigDecimal finalPrice, String winnerName) {
            this.auctionId = auctionId;
            this.finalPrice = finalPrice;
            this.winnerName = winnerName;
            this.endTime = LocalDateTime.now();
        }

        @JsonProperty("auctionId")
        public Long getAuctionId() {
            return auctionId;
        }

        @JsonProperty("finalPrice")
        public java.math.BigDecimal getFinalPrice() {
            return finalPrice;
        }

        @JsonProperty("winnerName")
        public String getWinnerName() {
            return winnerName;
        }

        @JsonProperty("endTime")
        public LocalDateTime getEndTime() {
            return endTime;
        }
    }
}