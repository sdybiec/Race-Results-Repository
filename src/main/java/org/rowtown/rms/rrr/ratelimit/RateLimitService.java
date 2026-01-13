package org.rowtown.rms.rrr.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing rate limits using a sliding window algorithm.
 */
@Service
@Slf4j
public class RateLimitService {

    @Value("${rate.limit.read.requests:100}")
    private int readLimit;

    @Value("${rate.limit.read.duration:60}")
    private int readDuration;

    @Value("${rate.limit.write.requests:20}")
    private int writeLimit;

    @Value("${rate.limit.write.duration:60}")
    private int writeDuration;

    @Value("${rate.limit.search.requests:30}")
    private int searchLimit;

    @Value("${rate.limit.search.duration:60}")
    private int searchDuration;

    private final Map<String, RateLimitBucket> buckets = new ConcurrentHashMap<>();

    /**
     * Check if a request is allowed based on rate limits.
     */
    public boolean allowRequest(String userId, RateLimitType type) {
        String key = userId + ":" + type;
        RateLimitBucket bucket = buckets.computeIfAbsent(key,
            k -> new RateLimitBucket(getLimit(type), getDuration(type)));

        return bucket.allowRequest();
    }

    /**
     * Get remaining requests for a user and limit type.
     */
    public int getRemaining(String userId, RateLimitType type) {
        String key = userId + ":" + type;
        RateLimitBucket bucket = buckets.get(key);
        if (bucket == null) {
            return getLimit(type);
        }
        return bucket.getRemaining();
    }

    /**
     * Get retry-after time in seconds.
     */
    public long getRetryAfter(String userId, RateLimitType type) {
        String key = userId + ":" + type;
        RateLimitBucket bucket = buckets.get(key);
        if (bucket == null) {
            return 0;
        }
        return bucket.getRetryAfter();
    }

    /**
     * Get the limit for a specific rate limit type.
     */
    public int getLimit(RateLimitType type) {
        return switch (type) {
            case READ -> readLimit;
            case WRITE -> writeLimit;
            case SEARCH -> searchLimit;
        };
    }

    /**
     * Get the duration (in seconds) for a specific rate limit type.
     */
    private int getDuration(RateLimitType type) {
        return switch (type) {
            case READ -> readDuration;
            case WRITE -> writeDuration;
            case SEARCH -> searchDuration;
        };
    }

    /**
     * Inner class representing a rate limit bucket using sliding window.
     */
    private static class RateLimitBucket {
        private final int maxRequests;
        private final int windowSeconds;
        private final Map<Long, Integer> requestCounts = new ConcurrentHashMap<>();

        public RateLimitBucket(int maxRequests, int windowSeconds) {
            this.maxRequests = maxRequests;
            this.windowSeconds = windowSeconds;
        }

        public synchronized boolean allowRequest() {
            long now = Instant.now().getEpochSecond();
            cleanOldEntries(now);

            int currentCount = getCurrentCount(now);
            if (currentCount >= maxRequests) {
                return false;
            }

            // Increment count for current second
            requestCounts.merge(now, 1, Integer::sum);
            return true;
        }

        public synchronized int getRemaining() {
            long now = Instant.now().getEpochSecond();
            cleanOldEntries(now);
            int currentCount = getCurrentCount(now);
            return Math.max(0, maxRequests - currentCount);
        }

        public synchronized long getRetryAfter() {
            long now = Instant.now().getEpochSecond();
            long oldestTimestamp = now - windowSeconds;

            // Find the oldest request in the window
            long earliestRequest = requestCounts.keySet().stream()
                .filter(timestamp -> timestamp > oldestTimestamp)
                .min(Long::compare)
                .orElse(now);

            // Calculate when that request will expire
            return Math.max(0, (earliestRequest + windowSeconds) - now);
        }

        private int getCurrentCount(long now) {
            long windowStart = now - windowSeconds;
            return requestCounts.entrySet().stream()
                .filter(entry -> entry.getKey() > windowStart)
                .mapToInt(Map.Entry::getValue)
                .sum();
        }

        private void cleanOldEntries(long now) {
            long windowStart = now - windowSeconds;
            requestCounts.entrySet().removeIf(entry -> entry.getKey() <= windowStart);
        }
    }
}
