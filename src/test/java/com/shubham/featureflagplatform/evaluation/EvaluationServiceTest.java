package com.shubham.featureflagplatform.evaluation;

import com.shubham.featureflagplatform.cache.CachedFlag;
import com.shubham.featureflagplatform.cache.FlagCache;
import com.shubham.featureflagplatform.cache.LocalFlagCache;
import com.shubham.featureflagplatform.config.CacheProperties;
import com.shubham.featureflagplatform.evaluation.dto.EvaluationRequest;
import com.shubham.featureflagplatform.evaluation.dto.EvaluationResponse;
import com.shubham.featureflagplatform.flag.Flag;
import com.shubham.featureflagplatform.flag.FlagRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvaluationServiceTest {

    @Mock FlagRepository flagRepository;
    @Mock FlagCache flagCache;

    // A real L1, not a mock: its merge rules are part of what evaluation relies on.
    LocalFlagCache l1;
    EvaluationService service;

    @BeforeEach
    void setUp() {
        l1 = new LocalFlagCache(new CacheProperties(300, 30, 60));
        service = new EvaluationService(flagRepository, flagCache, l1, new SimpleMeterRegistry());
    }

    private EvaluationResponse evaluate(String key) {
        return service.evaluate(new EvaluationRequest(key, "prod", "user-1", Map.of()));
    }

    @Test
    void l1Hit_touchesNeitherRedisNorDb() {
        l1.merge("prod", "checkout-v2", new CachedFlag(1, true, true, 100, List.of(), 1));

        EvaluationResponse res = evaluate("checkout-v2");

        assertThat(res.reason()).isEqualTo("ROLLOUT_MATCHED");
        verifyNoInteractions(flagCache, flagRepository);
    }

    @Test
    void redisHit_neverTouchesRepository_andWarmsL1() {
        CachedFlag cached = new CachedFlag(1, true, true, 100, List.of(), 1);
        when(flagCache.get("prod", "checkout-v2")).thenReturn(Optional.of(cached));

        evaluate("checkout-v2");

        verifyNoInteractions(flagRepository);
        assertThat(l1.get("prod", "checkout-v2")).contains(cached);
    }

    @Test
    void dbMiss_loadsFromDb_populatesRedisAndL1() {
        when(flagCache.get("prod", "checkout-v2")).thenReturn(Optional.empty());
        Flag flag = new Flag("checkout-v2", "prod", "New checkout", "desc", true, 100);
        // A flag loaded from the DB always has an id. Without this, getId() is null and
        // unboxing it into CachedFlag's long flagId throws NullPointerException.
        ReflectionTestUtils.setField(flag, "id", 1L);
        when(flagRepository.findByKeyAndEnvironment("checkout-v2", "prod")).thenReturn(Optional.of(flag));

        EvaluationResponse res = evaluate("checkout-v2");

        assertThat(res.enabled()).isTrue();
        verify(flagCache).put(eq("prod"), eq("checkout-v2"), any(CachedFlag.class));
        assertThat(l1.get("prod", "checkout-v2")).isPresent();
    }

    @Test
    void unknownFlag_negativeCachesInRedis_butNotInL1() {
        when(flagCache.get("prod", "nope")).thenReturn(Optional.empty());
        when(flagRepository.findByKeyAndEnvironment("nope", "prod")).thenReturn(Optional.empty());

        EvaluationResponse res = evaluate("nope");

        assertThat(res.reason()).isEqualTo("FLAG_NOT_FOUND");
        verify(flagCache).putAbsent("prod", "nope");
        assertThat(l1.get("prod", "nope")).isEmpty();
    }

    @Test
    void deletedFlag_tombstoneInL1_returnsNotFound_withoutLookups() {
        l1.merge("prod", "checkout-v2", CachedFlag.tombstone(1, 3));

        assertThat(evaluate("checkout-v2").reason()).isEqualTo("FLAG_NOT_FOUND");
        verifyNoInteractions(flagCache, flagRepository);
    }
}