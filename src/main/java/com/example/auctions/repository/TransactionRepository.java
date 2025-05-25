package com.example.auctions.repository;

import com.example.auctions.model.Transaction;
import com.example.auctions.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByBuyerOrderByTransactionDateDesc(User buyer);
    List<Transaction> findBySellerOrderByTransactionDateDesc(User seller);
    
    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.seller LEFT JOIN FETCH t.buyer WHERE t.id = :id")
    Optional<Transaction> findByIdWithUsers(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(t.price), 0) FROM Transaction t WHERE t.buyer = :buyer")
    BigDecimal sumTotalSpentByBuyer(@Param("buyer") User buyer);

    @Query("SELECT COALESCE(SUM(t.price), 0) FROM Transaction t WHERE t.seller = :seller")
    BigDecimal sumTotalSalesBySeller(@Param("seller") User seller);

    @Query("SELECT COALESCE(SUM(t.price), 0) FROM Transaction t WHERE t.seller = :seller AND t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal sumTotalSalesInPeriod(
        @Param("seller") User seller,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COALESCE(AVG(t.price), 0) FROM Transaction t WHERE t.seller = :seller AND t.transactionDate BETWEEN :startDate AND :endDate")
    BigDecimal calculateAveragePriceInPeriod(
        @Param("seller") User seller,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate);
}