package com.MyApp.PayFlow.controller;

import com.MyApp.PayFlow.dto.WalletBalanceResponse;
import com.MyApp.PayFlow.entity.TransactionEntity;
import com.MyApp.PayFlow.entity.UserEntity;
import com.MyApp.PayFlow.entity.WalletEntity;
import com.MyApp.PayFlow.repo.TransactionRepo;
import com.MyApp.PayFlow.repo.UserRepository;
import com.MyApp.PayFlow.repo.WalletRepo;
import com.MyApp.PayFlow.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user/wallet")
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    /**
     * Returns the wallet balance belonging to the authenticated user.
     *
     * @param authentication authenticated user's principal
     * @return current wallet balance
     */
    @GetMapping("/balance")
    public ResponseEntity<WalletBalanceResponse> getBalance(
            Authentication authentication) {

        String email = authentication.getName();

        // Resolve the authenticated user to obtain the internal user ID.
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        UUID userId = user.getId();

        // Delegate balance retrieval to the wallet service.
        return ResponseEntity.ok(walletService.getWalletBalance(userId));
    }

    /**
     * Initiates an add-money operation.
     *
     * The idempotency key helps the service identify repeated requests
     * so that a client retry does not unintentionally create duplicate payments.
     *
     * @param request request body containing the amount
     * @param idempotencyKey unique key supplied by the client
     * @param authentication authenticated user's principal
     * @return transaction identifier and initial processing status
     */
    @PostMapping("/add-money")
    public ResponseEntity<Map<String, Object>> addMoney(
            @RequestBody Map<String, Object> request,
            @RequestHeader("Idempotency-key") String idempotencyKey,
            Authentication authentication) {

        String email = authentication.getName();

        // Convert the supplied amount into BigDecimal for monetary calculations.
        BigDecimal amount = new BigDecimal(request.get("amount").toString());

        // The service creates the transaction and publishes the payment event.
        TransactionEntity tx =
                walletService.addMoney(email, amount, idempotencyKey);

        Map<String, Object> response = new HashMap<>();
        response.put("transactionId", tx.getId());
        response.put("status", tx.getStatus());
        response.put("message", "PaymentEvent processing");

        return ResponseEntity.ok(response);
    }

    /**
     * Transfers money from the authenticated user's wallet to another user.
     *
     * @param idempotencyKey unique key supplied by the client
     * @param request contains the receiver's email and transfer amount
     * @param authentication authenticated sender's principal
     * @return transaction identifier, status, and amount
     */
    @PostMapping("/transfer")
    public ResponseEntity<?> transferMoney(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        // The sender is determined from the authenticated principal.
        String senderEmail = authentication.getName();

        // Extract the receiver and amount from the request.
        String receiverEmail = request.get("email").toString();
        BigDecimal amount = new BigDecimal(request.get("amount").toString());

        // Delegate validation and transfer processing to the service layer.
        TransactionEntity tx = walletService.transferMoney(
                senderEmail,
                receiverEmail,
                amount,
                idempotencyKey
        );

        Map<String, String> response = new HashMap<>();
        response.put("transactionId", tx.getId().toString());
        response.put("status", tx.getStatus().name());
        response.put("amount", tx.getAmount().toString());

        return ResponseEntity.ok(response);
    }
}

