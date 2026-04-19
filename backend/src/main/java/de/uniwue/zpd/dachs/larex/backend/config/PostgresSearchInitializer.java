package de.uniwue.zpd.dachs.larex.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresSearchInitializer {

    private static final Logger log = LoggerFactory.getLogger(PostgresSearchInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public PostgresSearchInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            jdbcTemplate.execute("ALTER TABLE page_text_content ADD COLUMN IF NOT EXISTS normalized_text TEXT");
            jdbcTemplate.execute("ALTER TABLE page_text_content ADD COLUMN IF NOT EXISTS search_vector tsvector");
            jdbcTemplate.execute("ALTER TABLE page_text_content ADD COLUMN IF NOT EXISTS comment_entry BOOLEAN NOT NULL DEFAULT FALSE");
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_page_text_content_search_vector
                    ON page_text_content USING GIN (search_vector)
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_page_text_content_normalized_trgm
                    ON page_text_content USING GIN (normalized_text gin_trgm_ops)
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_page_text_content_comment_entry
                    ON page_text_content (comment_entry)
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_search_lexicon_workspace_project
                    ON search_lexicon_entries (workspace_id, project_id)
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_search_lexicon_normalized_trgm
                    ON search_lexicon_entries USING GIN (normalized_token gin_trgm_ops)
                    """);
            jdbcTemplate.execute("""
                    UPDATE page_text_content
                    SET normalized_text = lower(regexp_replace(coalesce(text_content, ''), '\\s+', ' ', 'g')),
                        search_vector = to_tsvector('simple', lower(regexp_replace(coalesce(text_content, ''), '\\s+', ' ', 'g'))),
                        comment_entry = coalesce(comment_entry, false)
                    WHERE normalized_text IS NULL OR search_vector IS NULL OR comment_entry IS NULL
                    """);
        } catch (Exception e) {
            log.warn("Failed to initialize PostgreSQL text search support: {}", e.getMessage());
        }
    }
}
