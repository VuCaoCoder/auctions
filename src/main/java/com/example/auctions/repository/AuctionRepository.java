package com.example.auctions.repository;

import com.example.auctions.model.Auction;
import com.example.auctions.model.AuctionStatus;
import com.example.auctions.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuctionRepository extends JpaRepository<Auction, Long> {
    Page<Auction> findBySeller(User seller, Pageable pageable);
    Page<Auction> findBySellerAndStatus(User seller, AuctionStatus status, Pageable pageable);
    Page<Auction> findByStatus(AuctionStatus status, Pageable pageable);
    List<Auction> findByStatus(AuctionStatus status);
    List<Auction> findByStatusAndEndTimeBefore(AuctionStatus status, LocalDateTime endTime);
    List<Auction> findBySellerId(Long sellerId);

    @Query("SELECT a FROM Auction a WHERE a.status = ?1 AND a.endTime > ?2")
    Page<Auction> findActiveAuctions(AuctionStatus status, LocalDateTime now, Pageable pageable);

    @Query("SELECT a FROM Auction a WHERE a.status = ?1 AND a.endTime > ?2 AND a.seller.id != ?3")
    Page<Auction> findActiveAuctionsForBuyer(AuctionStatus status, LocalDateTime now, Long buyerId, Pageable pageable);

    @Query("SELECT a FROM Auction a WHERE a.winner.id = ?1")
    List<Auction> findByWinnerId(Long winnerId);

    List<Auction> findBySellerAndStatusOrderByEndTimeDesc(User seller, AuctionStatus status);

    List<Auction> findByWinner(User winner);
} 