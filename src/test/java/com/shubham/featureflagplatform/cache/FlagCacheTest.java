package com.shubham.featureflagplatform.cache;

import com.shubham.featureflagplatform.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlagCacheTest {

    @Mock RedisTemplate<String, CachedFlag> redis;
    @Mock ValueOperations<String, CachedFlag> valueOps;

    FlagCache cache;

    @BeforeEach
    void setUp() {
        cache = new FlagCache(redis, new CacheProperties(300, 30));
    }

    @Test
    void get_returnsEmpty_whenRedisThrows() {                 // graceful degradation
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("redis down"));

        assertThat(cache.get("prod", "checkout-v2")).isEmpty();
    }

    @Test
    void put_usesFlagTtl_andCorrectKey() {
        when(redis.opsForValue()).thenReturn(valueOps);
        CachedFlag value = new CachedFlag(true, true, 50, List.of(), 1);

        cache.put("prod", "checkout-v2", value);

        verify(valueOps).set("flag:prod:checkout-v2", value, Duration.ofSeconds(300));
    }

    @Test
    void putAbsent_usesNegativeTtl_andSentinel() {
        when(redis.opsForValue()).thenReturn(valueOps);

        cache.putAbsent("prod", "missing");

        verify(valueOps).set("flag:prod:missing", CachedFlag.ABSENT, Duration.ofSeconds(30));
    }

    @Test
    void evict_deletesKey() {
        cache.evict("prod", "checkout-v2");
        verify(redis).delete("flag:prod:checkout-v2");
    }
}