package com.example.notifications.ratelimit;

import com.example.notifications.domain.Channel;

public interface ChannelRateLimiter {
    boolean tryAcquire(Channel channel);
    void acquire(Channel channel) throws InterruptedException;
}
