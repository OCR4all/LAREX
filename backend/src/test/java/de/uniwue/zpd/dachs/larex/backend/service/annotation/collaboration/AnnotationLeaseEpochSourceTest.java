package de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class AnnotationLeaseEpochSourceTest {
    private PlatformTransactionManager transactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        return manager;
    }

    @Test
    void allocatesEachVersionInAnIndependentCommittedWritableTransaction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L, 2L);
        var manager = transactionManager();
        var source = new AnnotationLeaseEpochSource(jdbc, manager);
        assertEquals(1L, source.nextEpoch());
        assertEquals(2L, source.nextEpoch());
        verify(manager, times(2)).getTransaction(argThat(definition ->
                definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW
                        && !definition.isReadOnly()));
        verify(manager, times(2)).commit(any());
    }

    @Test
    void concurrentAllocationsRemainUnique() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        var databaseSequence = new AtomicLong();
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenAnswer(invocation -> databaseSequence.incrementAndGet());
        var source = new AnnotationLeaseEpochSource(jdbc, transactionManager());
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = IntStream.range(0, 1000)
                    .mapToObj(ignored -> (Callable<Long>) source::nextEpoch).toList();
            var epochs = new HashSet<Long>();
            for (var future : executor.invokeAll(tasks)) epochs.add(future.get());
            assertEquals(1000, epochs.size());
        }
    }

    @Test
    void doesNotPublishAVersionWhoseAllocationFailedToCommit() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(1L, 2L);
        var manager = transactionManager();
        doThrow(new TransactionSystemException("Commit failed"))
                .doNothing().when(manager).commit(any());
        var source = new AnnotationLeaseEpochSource(jdbc, manager);
        assertThrows(TransactionSystemException.class, source::nextEpoch);
        assertEquals(2L, source.nextEpoch());
    }
}
