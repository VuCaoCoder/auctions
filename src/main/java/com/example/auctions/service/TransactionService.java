package com.example.auctions.service;

import com.example.auctions.model.Transaction;
import com.example.auctions.model.User;
import com.example.auctions.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TransactionService {
    private final TransactionRepository transactionRepository;

    @Autowired
    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public List<Transaction> getPurchaseHistory(User buyer) {
        return transactionRepository.findByBuyerOrderByTransactionDateDesc(buyer);
    }

    public List<Transaction> getSaleHistory(User seller) {
        return transactionRepository.findBySellerOrderByTransactionDateDesc(seller);
    }

    public Transaction saveTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }
} 