package com.shubham.featureflagplatform.cache;

import com.shubham.featureflagplatform.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFlagCacheTest {

    private static final long TTL_NANOS = Duration.ofSeconds(60).toNanos();

    private final AtomicLong clock = new AtomicLong(1_000);   // fake System.nanoTime()
    private LocalFlagCache cache;

    @BeforeEach
    void setUp() {
        cache = new LocalFlagCache(new CacheProperties(300, 30, 60), clock::get);
    }

    private static CachedFlag flag(long flagId, long version, boolean enabled) {
        return new CachedFlag(flagId, true, enabled, 100, List.of(), version);
    }

    @Test
    void newerVersion_replacesOlder() {
        cache.merge("prod", "f", flag(1, 1, false));
        cache.merge("prod", "f", flag(1, 2, true));

        assertThat(cache.get("prod", "f")).get().extracting(CachedFlag::version).isEqualTo(2L);
    }

    @Test
    void olderOrDuplicateVersion_isIgnored_andCurrentIsReturned() {
        CachedFlag v2 = flag(1, 2, true);
        cache.merge("prod", "f", v2);

        assertThat(cache.merge("prod", "f", flag(1, 1, false))).isSameAs(v2);   // late event
        assertThat(cache.merge("prod", "f", flag(1, 2, false))).isSameAs(v2);   // duplicate
    }

    @Test
    void tombstone_blocksLateOlderSnapshot() {
        cache.merge("prod", "f", CachedFlag.tombstone(1, 3));
        cache.merge("prod", "f", flag(1, 2, true));            // arrives after the delete

        assertThat(cache.get("prod", "f")).get().extracting(CachedFlag::present).isEqualTo(false);
    }

    @Test
    void recreatedFlag_withHigherId_beatsOldTombstone() {
        cache.merge("prod", "f", CachedFlag.tombstone(1, 8));
        cache.merge("prod", "f", flag(2, 1, true));            // new row: id 2, back to v1

        assertThat(cache.get("prod", "f")).get().extracting(CachedFlag::flagId).isEqualTo(2L);
    }

    @Test
    void expiredEntry_missesOnRead_butStillRejectsOlderReload() {
        cache.merge("prod", "f", flag(1, 5, true));
        clock.addAndGet(TTL_NANOS);                             // now exactly at expiry

        assertThat(cache.get("prod", "f")).isEmpty();
        assertThat(cache.merge("prod", "f", flag(1, 4, false)).version()).isEqualTo(5L);
    }

    @Test
    void expiredEntry_isRenewed_bySameVersionReload() {
        cache.merge("prod", "f", flag(1, 5, true));
        clock.addAndGet(TTL_NANOS);

        CachedFlag reloaded = flag(1, 5, true);
        assertThat(cache.merge("prod", "f", reloaded)).isSameAs(reloaded);
        assertThat(cache.get("prod", "f")).isPresent();
    }

    @Test
    void zeroTtl_disablesL1() {
        LocalFlagCache off = new LocalFlagCache(new CacheProperties(300, 30, 0), clock::get);
        off.merge("prod", "f", flag(1, 1, true));

        assertThat(off.get("prod", "f")).isEmpty();
    }
}