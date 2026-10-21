
package com.MyApp.PayFlow.service;

import com.MyApp.PayFlow.dto.ScheduledPaymentDto;
import com.MyApp.PayFlow.dto.TransactinoHistoryDto;
import com.MyApp.PayFlow.entity.ScheduledPaymentEntity;
import com.MyApp.PayFlow.entity.TransactionEntity;
import com.MyApp.PayFlow.entity.UserEntity;
import com.MyApp.PayFlow.repo.ScheduledPaymentRepository;
import com.MyApp.PayFlow.repo.TransactionRepo;
import com.MyApp.PayFlow.repo.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionHistoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepo transactionRepo;
    private final UserRepository userRepo;
    private final ScheduledPaymentRepository scheduledPaymentRepo;

    /**
     * Retrieves paginated transaction history for the authenticated user.
     *
     * Both date boundaries must be provided together when filtering.
     * Results are ordered by creation time, newest first.
     *
     * @param email email requested by the caller
     * @param page zero-based page number
     * @param size number of records per page
     * @param dateFrom optional inclusive start timestamp
     * @param dateTo optional inclusive end timestamp
     * @return a page of transaction history DTOs
     */
    public Page<TransactinoHistoryDto> getUserTransactionHistory(
            String email,
            int page,
            int size,
            String dateFrom,
            String dateTo) {

        validatePagination(page, size);
        validateRequestedEmail(email);

        UserEntity user = userRepo.findByEmail(email)
                .orElseThrow(() ->
                        new EntityNotFoundException("User not found"));

        // Parse and validate the optional date range.
        Instant from = parseTimestamp(dateFrom, "dateFrom");
        Instant to = parseTimestamp(dateTo, "dateTo");

        validateDateRange(from, to);

        Pageable pageable = PageRequest.of(page, size);

        Page<TransactionEntity> transactions;

        if (from != null) {
            transactions =
                    transactionRepo
                            .findByUserAndCreatedAtBetweenOrderByCreatedAtDesc(
                                    user, from, to, pageable);
        } else {
            transactions =
                    transactionRepo
                            .findByUserOrderByCreatedAtDesc(user, pageable);
        }

        // Return DTOs instead of entities to avoid exposing persistence
        // details and accidentally serializing entity relationships.
        return transactions.map(tx -> new TransactinoHistoryDto(
                tx.getId(),
                tx.getAmount(),
                tx.getType().name(),
                tx.getCreatedAt()
        ));
    }

    /**
     * Retrieves scheduled payments belonging to the authenticated user.
     *
     * @param page zero-based page number
     * @param size number of records per page
     * @param dateFrom optional inclusive start timestamp
     * @param dateTo optional inclusive end timestamp
     * @return a page of scheduled payment DTOs
     */
    public Page<ScheduledPaymentDto> getScheduledPayments(
            int page,
            int size,
            String dateFrom,
            String dateTo) {

        validatePagination(page, size);

        String email = getAuthenticatedEmail();

        UserEntity user = userRepo.findByEmail(email)
                .orElseThrow(() ->
                        new EntityNotFoundException("User not found"));

        Instant from = parseTimestamp(dateFrom, "dateFrom");
        Instant to = parseTimestamp(dateTo, "dateTo");

        validateDateRange(from, to);

        Pageable pageable = PageRequest.of(page, size);

        Page<ScheduledPaymentEntity> payments;

        if (from != null) {
            payments =
                    scheduledPaymentRepo
                            .findBySenderAndScheduledAtBetweenOrderByScheduledAtDesc(
                                    user, from, to, pageable);
        } else {
            payments =
                    scheduledPaymentRepo
                            .findBySenderOrderByScheduledAtDesc(
                                    user, pageable);
        }

        return payments.map(payment -> new ScheduledPaymentDto(
                payment.getId(),
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getAmount(),
                payment.getScheduledAt(),
                payment.getLastExecuted(),
                payment.getNextExecution(),
                payment.getStatus().name(),
                payment.getScheduleType().name()
        ));
    }

    /**
     * Obtains the authenticated user's email from Spring Security.
     */
    private String getAuthenticatedEmail() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {

            throw new AccessDeniedException(
                    "Authentication is required");
        }

        return authentication.getName();
    }

    /**
     * Ensures callers cannot retrieve another user's transaction history
     * by supplying a different email address.
     */
    private void validateRequestedEmail(String requestedEmail) {

        String authenticatedEmail = getAuthenticatedEmail();

        if (requestedEmail == null
                || !authenticatedEmail.equalsIgnoreCase(
                requestedEmail.trim())) {

            throw new AccessDeniedException(
                    "You cannot access another user's transaction history");
        }
    }

    /**
     * Validates pagination parameters before querying the database.
     */
    private void validatePagination(int page, int size) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page number cannot be negative");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and "
                            + MAX_PAGE_SIZE);
        }
    }

    /**
     * Parses an optional ISO-8601 timestamp.
     *
     * Example: 2026-10-01T00:00:00Z
     */
    private Instant parseTimestamp(String value, String fieldName) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(value.trim());
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    fieldName + " must be a valid ISO-8601 timestamp",
                    exception);
        }
    }

    /**
     * Requires both date boundaries or neither, and ensures that the
     * start timestamp does not occur after the end timestamp.
     */
    private void validateDateRange(Instant from, Instant to) {

        if ((from == null) != (to == null)) {
            throw new IllegalArgumentException(
                    "Both dateFrom and dateTo must be provided together");
        }

        if (from != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "dateFrom cannot be after dateTo");
        }
    }
}
