package com.ecomtest.service;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class OrderConcurrencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String productPayload(String sku, int stock) {
        return """
                {
                  "name": "Contended Item",
                  "sku": "%s",
                  "price": 9.99,
                  "stock": %d,
                  "status": "ACTIVE"
                }
                """.formatted(sku, stock);
    }

    private String orderPayload(Long productId) {
        return """
                {
                  "customerName": "Concurrent Buyer",
                  "productId": %d,
                  "quantity": 1,
                  "unitPrice": 9.99,
                  "status": "PENDING"
                }
                """.formatted(productId);
    }

    @Test
    void fiftyConcurrentOrdersOnStockOneYieldExactlyOneSuccess() throws Exception {
        String productResponse = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload("SKU-CONCURRENCY-1", 1)))
                .andReturn().getResponse().getContentAsString();
        Long productId = objectMapper.readTree(productResponse).get("id").asLong();

        int concurrentRequests = 50;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch readyLatch = new CountDownLatch(concurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < concurrentRequests; i++) {
            tasks.add(() -> {
                readyLatch.countDown();
                startLatch.await();
                return mockMvc.perform(post("/api/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(orderPayload(productId)))
                        .andReturn().getResponse().getStatus();
            });
        }

        List<Future<Integer>> futures = new ArrayList<>();
        for (Callable<Integer> task : tasks) {
            futures.add(executor.submit(task));
        }

        readyLatch.await();
        startLatch.countDown();

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        for (Future<Integer> future : futures) {
            int status = future.get();
            if (status == 201) {
                successCount.incrementAndGet();
            } else if (status == 409) {
                conflictCount.incrementAndGet();
            }
        }
        executor.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(concurrentRequests - 1, conflictCount.get());

        String productAfter = mockMvc.perform(get("/api/products/{id}", productId))
                .andReturn().getResponse().getContentAsString();
        assertEquals(0, objectMapper.readTree(productAfter).get("stock").asInt());
    }
}
