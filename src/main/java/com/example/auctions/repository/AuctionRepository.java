package com.example.auctions.repository;

import com.example.auctions.model.Auction;
import com.example.auctions.model.AuctionStatus;
import com.example.auctions.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    long countBySellerAndStatus(User seller, AuctionStatus status);
    
    long countBySellerAndStatusAndWinnerIsNotNull(User seller, AuctionStatus status);

    @Query("SELECT COUNT(DISTINCT a) FROM Auction a JOIN a.bids b WHERE b.bidder = :user")
    long countWatchedAuctionsByUser(@Param("user") User user);

    @Query("SELECT COUNT(a) FROM Auction a " +
           "WHERE a.seller = :seller " +
           "AND a.status = 'ENDED' " +
           "AND a.winner IS NOT NULL " +
           "AND a.endTime BETWEEN :startDate AND :endDate")
    long countSuccessfulAuctionsInPeriod(
        @Param("seller") User seller,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(a) FROM Auction a " +
           "WHERE a.seller = :seller " +
           "AND a.createdAt BETWEEN :startDate AND :endDate")
    long countAuctionsCreatedInPeriod(
        @Param("seller") User seller,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(AVG(SIZE(a.bids)), 0) FROM Auction a " +
           "WHERE a.seller = :seller " +
           "AND a.createdAt BETWEEN :startDate AND :endDate")
    double calculateAverageBiddersInPeriod(
        @Param("seller") User seller,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
} 