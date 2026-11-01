package com.shubham.featureflagplatform.flag;

import com.shubham.featureflagplatform.audit.AuditLogRepository;
import com.shubham.featureflagplatform.cache.CachedFlag;
import com.shubham.featureflagplatform.cache.FlagCache;
import com.shubham.featureflagplatform.cache.LocalFlagCache;
import com.shubham.featureflagplatform.event.ChangeType;
import com.shubham.featureflagplatform.event.FlagChangedEvent;
import com.shubham.featureflagplatform.flag.dto.UpdateFlagRequest;
import com.shubham.featureflagplatform.outbox.FlagEventOutbox;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * No transaction is active in a plain unit test, so afterCommit() runs its action at once
 * (the fallback branch). That lets us check the after-commit effects directly.
 */
@ExtendWith(MockitoExtension.class)
class FlagServiceTest {

    @Mock FlagRepository flagRepository;
    @Mock AuditLogRepository auditLogRepository;
    @Mock FlagCache flagCache;
    @Mock LocalFlagCache localFlagCache;
    @Mock FlagEventOutbox outbox;

    FlagService service;

    @BeforeEach
    void setUp() {
        service = new FlagService(flagRepository, auditLogRepository, flagCache, localFlagCache, outbox);
    }

    /** A flag "loaded from the DB": id 7, at version 4. */
    private static Flag storedFlag() {
        Flag flag = new Flag("checkout-v2", "prod", "New checkout", "desc", true, 50);
        ReflectionTestUtils.setField(flag, "id", 7L);
        ReflectionTestUtils.setField(flag, "version", 4L);
        return flag;
    }

    private FlagChangedEvent capturedEvent() {
        ArgumentCaptor<FlagChangedEvent> captor = ArgumentCaptor.forClass(FlagChangedEvent.class);
        verify(outbox).append(captor.capture());
        return captor.getValue();
    }

    @Test
    void update_flushesBeforeBuildingEvent_andAppendsSnapshot() {
        Flag flag = storedFlag();
        when(flagRepository.findById(7L)).thenReturn(Optional.of(flag));
        // Pretend to be Hibernate: the flush runs the UPDATE and bumps the version.
        doAnswer(inv -> { ReflectionTestUtils.setField(flag, "version", 5L); return null; })
                .when(flagRepository).flush();

        service.update(7L, new UpdateFlagRequest("New checkout", "desc", true, 25, List.of()));

        InOrder order = inOrder(flagRepository, outbox);
        order.verify(flagRepository).flush();
        order.verify(outbox).append(any());

        FlagChangedEvent event = capturedEvent();
        assertThat(event.changeType()).isEqualTo(ChangeType.UPDATED);
        assertThat(event.version()).isEqualTo(5L);                    // the bumped version
        assertThat(event.snapshot().rolloutPercentage()).isEqualTo(25);
    }

    @Test
    void delete_appendsTombstone_withVersionPlusOne() {
        when(flagRepository.findById(7L)).thenReturn(Optional.of(storedFlag()));

        service.delete(7L);

        FlagChangedEvent event = capturedEvent();
        assertThat(event.changeType()).isEqualTo(ChangeType.DELETED);
        assertThat(event.version()).isEqualTo(5L);
        assertThat(event.snapshot()).isNull();
    }

    @Test
    void write_evictsRedis_andUpdatesOwnL1() {
        when(flagRepository.findById(7L)).thenReturn(Optional.of(storedFlag()));

        service.delete(7L);

        verify(flagCache).evict("prod", "checkout-v2");
        verify(localFlagCache).merge(eq("prod"), eq("checkout-v2"),
                argThat((CachedFlag c) -> !c.present() && c.version() == 5L));
    }
}