package de.uniwue.zpd.dachs.larex.backend.service.annotation.collaboration;

import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Allocates ordered lease versions that survive backend restarts. */
@Component
@DependsOnDatabaseInitialization
public class AnnotationLeaseEpochSource {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate allocationTransaction;

    public AnnotationLeaseEpochSource(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        allocationTransaction = new TransactionTemplate(transactionManager);
        allocationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        allocationTransaction.setReadOnly(false);
    }

    public long nextEpoch() {
        // Commit before publishing a version, independently of the caller's transaction.
        Long epoch = allocationTransaction.execute(status ->
                jdbcTemplate.queryForObject("SELECT nextval('annotation_lease_epochs')", Long.class));
        if (epoch == null) {
            throw new IllegalStateException("No annotation lease epoch was allocated");
        }
        return epoch;
    }
}
