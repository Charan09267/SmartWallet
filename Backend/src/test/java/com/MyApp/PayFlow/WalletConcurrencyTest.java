package com.MyApp.PayFlow;

import com.MyApp.PayFlow.service.WalletService;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class WalletConcurrencyTest {

    @Autowired
    private WalletService walletService;

    @Test
    void testConcurrentTransfers() throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(5);

        int numberOfThreads = 5;
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    walletService.transferMoney(
                            "chiranjeevi@gmail.com",
                            "charanimmati@gmail.com",
                            new BigDecimal("300")
                    );
                } catch (Exception e) {
                    System.out.println("Failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
    }
}
