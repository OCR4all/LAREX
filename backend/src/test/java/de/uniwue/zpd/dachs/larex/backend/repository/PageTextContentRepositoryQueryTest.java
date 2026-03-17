package de.uniwue.zpd.dachs.larex.backend.repository;

import de.uniwue.zpd.dachs.larex.backend.repository.page.PageTextContentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;


import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTextContentRepositoryQueryTest {

    @Test
    void matchingTextLineIdsQuery_excludesNullTextLineIds() throws Exception {
        Method method = PageTextContentRepository.class.getMethod(
            "findTextLineIdsByPageIdAndTextContentContaining",
            String.class,
            String.class
        );

        Query query = method.getAnnotation(Query.class);
        assertNotNull(query);
        assertTrue(query.value().contains("p.textLineId IS NOT NULL"));
    }

    @Test
    void matchingTextRegionIdsQuery_excludesNullRegionIds() throws Exception {
        Method method = PageTextContentRepository.class.getMethod(
            "findRegionIdsByPageIdAndTextContentContaining",
            String.class,
            String.class
        );

        Query query = method.getAnnotation(Query.class);
        assertNotNull(query);
        assertTrue(query.value().contains("p.regionId IS NOT NULL"));
    }
}
