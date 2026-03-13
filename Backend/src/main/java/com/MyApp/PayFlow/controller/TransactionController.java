package com.MyApp.PayFlow.controller;

import com.MyApp.PayFlow.dto.ScheduledPaymentDto;
import com.MyApp.PayFlow.dto.TransactinoHistoryDto;
import com.MyApp.PayFlow.entity.TransactionEntity;
import com.MyApp.PayFlow.repo.TransactionRepo;
import com.MyApp.PayFlow.service.TransactionHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/v1/user/transactions")
public class TransactionController {

    private final TransactionRepo transactionRepo;
    private final TransactionHistoryService historyService;

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

    @GetMapping
    public Page<TransactinoHistoryDto> getTransactionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Authentication authentication
    ) {
        String email = authentication.getName();
        return historyService.getUserTransactionHistory(email , page, size, dateFrom, dateTo);
    }

    @GetMapping("/scheduled-payments")
    public Page<ScheduledPaymentDto> getScheduledPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo
    ) {
        return historyService.getScheduledPayments(page, size, dateFrom, dateTo);
    }
}
