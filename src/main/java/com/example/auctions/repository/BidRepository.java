package com.example.auctions.repository;

import com.example.auctions.model.Auction;
import com.example.auctions.model.AuctionStatus;
import com.example.auctions.model.Bid;
import com.example.auctions.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

@Repository
public interface BidRepository extends JpaRepository<Bid, Long> {
    List<Bid> findByAuctionOrderByAmountDesc(Auction auction);
    
    @Query("SELECT b FROM Bid b WHERE b.auction = :auction AND b.amount = (SELECT MAX(b2.amount) FROM Bid b2 WHERE b2.auction = :auction)")
    Optional<Bid> findTopByAuctionOrderByAmountDesc(@Param("auction") Auction auction);
    
    List<Bid> findByBidderIdOrderByBidTimeDesc(Long bidderId);
    
    @Query("SELECT b FROM Bid b WHERE b.bidder.id = :bidderId AND b.auction.status = :status ORDER BY b.bidTime DESC")
    List<Bid> findByBidderIdAndAuctionStatusOrderByBidTimeDesc(@Param("bidderId") Long bidderId, @Param("status") AuctionStatus status);
    
    @Query("SELECT DISTINCT a FROM Auction a WHERE a.status = 'ENDED' AND EXISTS (SELECT 1 FROM Bid b WHERE b.auction = a AND b.bidder.id = :bidderId AND b.amount = (SELECT MAX(b2.amount) FROM Bid b2 WHERE b2.auction = a))")
    List<Auction> findWonAuctionsByBidderId(@Param("bidderId") Long bidderId);
    
    @Query("SELECT DISTINCT a FROM Auction a WHERE a.status = 'ENDED' AND EXISTS (SELECT 1 FROM Bid b WHERE b.auction = a AND b.bidder.id = :bidderId AND b.amount = (SELECT MAX(b2.amount) FROM Bid b2 WHERE b2.auction = a))")
    Page<Auction> findWonAuctionsByBidderIdPaged(@Param("bidderId") Long bidderId, Pageable pageable);

    @Query("SELECT COUNT(DISTINCT b.auction) FROM Bid b WHERE b.bidder = :user AND b.auction.endTime > :currentTime")
    long countDistinctAuctionsByBidderAndAuctionEndTimeAfter(@Param("user") User user, @Param("currentTime") LocalDateTime currentTime);

    @Query("SELECT COUNT(DISTINCT b.auction) FROM Bid b WHERE b.bidder = :user AND b.amount = (SELECT MAX(b2.amount) FROM Bid b2 WHERE b2.auction = b.auction)")
    long countAuctionsWhereUserIsHighestBidder(@Param("user") User user);

    @Query("SELECT COUNT(DISTINCT b.auction) FROM Bid b WHERE b.bidder = :user AND b.auction.endTime < CURRENT_TIMESTAMP AND b.amount = (SELECT MAX(b2.amount) FROM Bid b2 WHERE b2.auction = b.auction)")
    long countAuctionsWonByUser(@Param("user") User user);
} 