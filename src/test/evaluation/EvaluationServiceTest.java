package com.shubham.featureflagplatform.evaluation;

import com.shubham.featureflagplatform.cache.CachedFlag;
import com.shubham.featureflagplatform.cache.FlagCache;
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

    EvaluationService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationService(flagRepository, flagCache, new SimpleMeterRegistry());
    }

    @Test
    void cacheHit_neverTouchesRepository() {
        CachedFlag cached = new CachedFlag(true, true, 100, List.of(), 1);
        when(flagCache.get("prod", "checkout-v2")).thenReturn(Optional.of(cached));

        EvaluationResponse res = service.evaluate(
                new EvaluationRequest("checkout-v2", "prod", "user-1", Map.of()));

        assertThat(res.enabled()).isTrue();
        assertThat(res.reason()).isEqualTo("ROLLOUT_MATCHED");
        verifyNoInteractions(flagRepository);            // the whole point of the cache
    }

    @Test
    void cacheMiss_loadsFromDb_andPopulatesCache() {
        when(flagCache.get("prod", "checkout-v2")).thenReturn(Optional.empty());
        Flag flag = new Flag("checkout-v2", "prod", "New checkout", "desc", true, 100);
        when(flagRepository.findByKeyAndEnvironment("checkout-v2", "prod"))
                .thenReturn(Optional.of(flag));

        EvaluationResponse res = service.evaluate(
                new EvaluationRequest("checkout-v2", "prod", "user-1", Map.of()));

        assertThat(res.enabled()).isTrue();
        verify(flagCache).put(eq("prod"), eq("checkout-v2"), any(CachedFlag.class));
    }

    @Test
    void unknownFlag_negativeCaches_andReturnsNotFound() {
        when(flagCache.get("prod", "nope")).thenReturn(Optional.empty());
        when(flagRepository.findByKeyAndEnvironment("nope", "prod")).thenReturn(Optional.empty());

        EvaluationResponse res = service.evaluate(
                new EvaluationRequest("nope", "prod", "user-1", Map.of()));

        assertThat(res.enabled()).isFalse();
        assertThat(res.reason()).isEqualTo("FLAG_NOT_FOUND");
        verify(flagCache).putAbsent("prod", "nope");
    }
}