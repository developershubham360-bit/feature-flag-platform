package com.shubham.featureflagplatform.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import com.shubham.featureflagplatform.config.CacheProperties;

import java.time.Duration;
import java.util.Optional;

@Component
public class FlagCache {

    private static final Logger log = LoggerFactory.getLogger(FlagCache.class);

    private final RedisTemplate<String, CachedFlag> redis;
    private final Duration flagTtl;
    private final Duration negativeTtl;

    public FlagCache(RedisTemplate<String, CachedFlag> flagRedisTemplate, CacheProperties props) {
        this.redis = flagRedisTemplate;
        this.flagTtl = Duration.ofSeconds(props.flagTtlSeconds());
        this.negativeTtl = Duration.ofSeconds(props.negativeTtlSeconds());
    }

    /** Cache read. Any Redis failure is swallowed and reported as a miss. */
    public Optional<CachedFlag> get(String environment, String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key(environment, key)));
        } catch (RuntimeException ex) {
            log.warn("Redis GET failed for {} — falling back to DB", key(environment, key), ex);
            return Optional.empty();
        }
    }

    /** Populate a real flag with the normal TTL. */
    public void put(String environment, String key, CachedFlag value) {
        try {
            redis.opsForValue().set(key(environment, key), value, flagTtl);
        } catch (RuntimeException ex) {
            log.warn("Redis PUT failed for {} — ignoring", key(environment, key), ex);
        }
    }

    /** Negative cache: remember "this flag doesn't exist" with a SHORT TTL. */
    public void putAbsent(String environment, String key) {
        try {
            redis.opsForValue().set(key(environment, key), CachedFlag.ABSENT, negativeTtl);
        } catch (RuntimeException ex) {
            log.warn("Redis PUT(absent) failed for {} — ignoring", key(environment, key), ex);
        }
    }

    /** Remove the entry (called AFTER a write commits — see FlagService). */
    public void evict(String environment, String key) {
        try {
            redis.delete(key(environment, key));
        } catch (RuntimeException ex) {
            log.warn("Redis EVICT failed for {} — ignoring", key(environment, key), ex);
        }
    }

    /** e.g. flag:prod:checkout-v2 — colon-delimited, namespaced, readable in redis-cli. */
    private String key(String environment, String key) {
        return "flag:" + environment + ":" + key;
    }
}