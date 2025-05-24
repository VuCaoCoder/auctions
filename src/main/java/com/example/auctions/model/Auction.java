package com.example.auctions.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "auctions")
public class Auction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Product name is required")
    @Size(min = 3, max = 100, message = "Product name must be between 3 and 100 characters")
    @Column(name = "product_name")
    private String productName;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 1000, message = "Description must be between 10 and 1000 characters")
    @Column(columnDefinition = "TEXT")
    private String description;

    @NotNull(message = "Starting price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Starting price must be greater than 0")
    @Column(name = "starting_price")
    private BigDecimal startingPrice;

    @Column(name = "current_price")
    private BigDecimal currentPrice;

    @NotNull(message = "End time is required")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    @Column(name = "end_time")
    private LocalDateTime endTime;

    @NotBlank(message = "Bank name is required")
    @Column(name = "bank_name")
    private String bankName;

    @NotBlank(message = "Bank account number is required")
    @Pattern(regexp = "^[0-9]{10,16}$", message = "Bank account number must be between 10 and 16 digits")
    private String bankAccount;

    @Column(name = "image")
    private String image;

    @Transient
    private MultipartFile imageFile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @OneToMany(mappedBy = "auction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Bid> bids = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private User winner;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private AuctionStatus status = AuctionStatus.DRAFT;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Transient
    private long uniqueBidders;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        currentPrice = startingPrice;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Custom validation for end time
    @AssertTrue(message = "End time must be in the future for active auctions")
    private boolean isEndTimeValid() {
        // Only validate future date for DRAFT auctions or when activating an auction
        if (status == AuctionStatus.DRAFT || (status == AuctionStatus.ACTIVE && id == null)) {
            return endTime != null && endTime.isAfter(LocalDateTime.now());
        }
        return true;
    }

    public void addBid(Bid bid) {
        bids.add(bid);
        bid.setAuction(this);
        currentPrice = bid.getAmount();
    }

    public void removeBid(Bid bid) {
        bids.remove(bid);
        bid.setAuction(null);
    }

    public long getUniqueBidders() {
        return uniqueBidders;
    }

    public void setUniqueBidders(long uniqueBidders) {
        this.uniqueBidders = uniqueBidders;
    }
}