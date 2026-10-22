package com.MyApp.PayFlow.controller;

import com.MyApp.PayFlow.dto.response.ScheduledPaymentDto;
import com.MyApp.PayFlow.dto.response.TransactinoHistoryDto;
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
@RequestMapping("/api/v1/user/transactions")
public class TransactionController {

    private final TransactionRepo transactionRepo;
    private final TransactionHistoryService historyService;

    /**
     * Retrieves the current status and basic details of a transaction.
     *
     * @param id transaction identifier
     * @return transaction ID, status, and amount
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTransactionStatus(
            @PathVariable UUID id) {

        // Retrieve the transaction or fail if the ID does not exist.
        TransactionEntity tx = transactionRepo.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Transaction not found"));

        Map<String, Object> response = new HashMap<>();
        response.put("transactionId", tx.getId());
        response.put("status", tx.getStatus());
        response.put("amount", tx.getAmount());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves paginated transaction history for the authenticated user.
     *
     * @param page zero-based page number
     * @param size maximum number of records requested
     * @param dateFrom optional start timestamp
     * @param dateTo optional end timestamp
     * @param authentication authenticated user's principal
     * @return a page of transaction history records
     */
    @GetMapping
    public Page<TransactinoHistoryDto> getTransactionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            Authentication authentication) {

        // Use the authenticated email to scope the history query.
        String email = authentication.getName();

        return historyService.getUserTransactionHistory(
                email, page, size, dateFrom, dateTo
        );
    }

    /**
     * Retrieves paginated scheduled-payment history for the current user.
     *
     * @param page zero-based page number
     * @param size maximum number of records requested
     * @param dateFrom optional start timestamp
     * @param dateTo optional end timestamp
     * @return a page of scheduled-payment records
     */
    @GetMapping("/scheduled-payments")
    public Page<ScheduledPaymentDto> getScheduledPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {

        // The history service obtains the current user's identity
        // from Spring Security, as implemented in the service.
        return historyService.getScheduledPayments(
                page, size, dateFrom, dateTo
        );
    }
}

