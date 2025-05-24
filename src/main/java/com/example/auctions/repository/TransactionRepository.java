package com.example.auctions.repository;

import com.example.auctions.model.Transaction;
import com.example.auctions.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByBuyerOrderByTransactionDateDesc(User buyer);
    List<Transaction> findBySellerOrderByTransactionDateDesc(User seller);
    
    @Query("SELECT t FROM Transaction t LEFT JOIN FETCH t.seller LEFT JOIN FETCH t.buyer WHERE t.id = :id")
    Optional<Transaction> findByIdWithUsers(@Param("id") Long id);
}