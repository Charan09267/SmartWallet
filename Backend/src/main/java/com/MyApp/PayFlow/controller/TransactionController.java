package com.MyApp.PayFlow.controller;

import com.MyApp.PayFlow.entity.TransactionEntity;
import com.MyApp.PayFlow.repo.TransactionRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/user/transaction")
public class TransactionController {

    private final TransactionRepo transactionRepo;

    @GetMapping("/{id}")
    public ResponseEntity<Map<String , Object>> getTransactionStatus(@PathVariable UUID id){
        TransactionEntity tx = transactionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        Map<String, Object> response = new HashMap<>();

        response.put("transactionId", tx.getId());
        response.put("status", tx.getStatus());
        response.put("amount", tx.getAmount());

        return ResponseEntity.ok(response);
    }
}
