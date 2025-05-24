package com.example.auctions.repository;

import com.example.auctions.model.Transaction;
import com.example.auctions.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByBuyerOrderByTransactionDateDesc(User buyer);
    List<Transaction> findBySellerOrderByTransactionDateDesc(User seller);
}