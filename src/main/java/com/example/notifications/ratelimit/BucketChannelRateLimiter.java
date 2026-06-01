package com.example.notifications.ratelimit;

import com.example.notifications.domain.Channel;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class BucketChannelRateLimiter implements ChannelRateLimiter {

    private final ConcurrentHashMap<Channel, Bucket> buckets = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        for (Channel channel : Channel.values()) {
            buckets.put(channel, buildBucket());
        }
        log.info("Rate limiter initialised — 100 tokens/minute per channel");
    }

    private Bucket buildBucket() {
        Bandwidth limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    @Override
    public boolean tryAcquire(Channel channel) {
        return buckets.get(channel).tryConsume(1);
    }

    @Override
    public void acquire(Channel channel) throws InterruptedException {
        Bucket bucket = buckets.get(channel);
        while (!bucket.tryConsume(1)) {
            Thread.sleep(100);
        }
    }

    /**
     * Package-private — used only by tests to reset bucket state.
     */
    void resetBucket(Channel channel) {
        buckets.put(channel, buildBucket());
    }
}
