// SPDX-FileCopyrightText: 2025 owlmaddie LLC
// SPDX-License-Identifier: GPL-3.0-or-later
// Assets CC-BY-NC-SA-4.0; CreatureChat™ trademark © owlmaddie LLC - unauthorized use prohibited
package com.owlmaddie.utils;

import java.util.concurrent.Semaphore;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The {@code RateLimiter} class is used to slow down LLM unit tests so we don't hit any rate limits accidentally.
 *
 * <p>Accepts a fractional rate (e.g. 0.5 = one request every 2 seconds = 30 req/min).
 * This keeps the test suite well within Anthropic Tier 1's 50 req/min cap for Haiku.
 */
public class RateLimiter {
    private final Semaphore semaphore;

    public RateLimiter(double requestsPerSecond) {
        semaphore = new Semaphore(1);
        long periodMs = (long) (1000.0 / requestsPerSecond);
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
            // Release one permit at a time so we never exceed the target rate.
            if (semaphore.availablePermits() == 0) {
                semaphore.release(1);
            }
        }, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    public void acquire() throws InterruptedException {
        semaphore.acquire();
    }
}