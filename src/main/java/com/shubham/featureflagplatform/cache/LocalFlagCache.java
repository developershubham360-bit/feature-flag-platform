package com.shubham.featureflagplatform.cache;

import com.shubham.featureflagplatform.config.CacheProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

@Component
public class LocalFlagCache {
    private record Entry(CachedFlag value, long expiresAtNanos) {
        boolean expired(long nowNanos) {
            return nowNanos - expiresAtNanos >= 0;
        }
    }
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final long ttlNanos;
    private final LongSupplier nanoClock;

    @Autowired
    public LocalFlagCache(CacheProperties props) {
        this(props, System::nanoTime);
    }

    LocalFlagCache(CacheProperties props, LongSupplier nanoClock) {
        this.ttlNanos = Duration.ofSeconds(props.localTtlSeconds()).toNanos();
        this.nanoClock = nanoClock;
    }

    public Optional<CachedFlag> get(String environment, String key) {
        Entry entry = entries.get(key(environment, key));
        if (entry == null || entry.expired(nanoClock.getAsLong())) {
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }

    public CachedFlag merge(String environment, String key, CachedFlag candidate) {
        long now = nanoClock.getAsLong();
        Entry winner = entries.compute(key(environment, key), (k, current) -> {
            if (current == null) {
                return new Entry(candidate, now + ttlNanos);
            }
            int order = candidate.compareVersion(current.value);
            if (order > 0 || (order == 0 && current.expired(now))) {
                return new Entry(candidate, now + ttlNanos);
            } 
            return current;
        });
        return winner.value;
    }

    private String key(String environment, String key) {
        return environment + ":" + key;
    }
}
