package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageXmlMappingTest {

    @Test
    void pageXmlOwnsTheOnlyOneToOneAssociation() throws NoSuchFieldException {
        Field pageField = PageXml.class.getDeclaredField("page");
        OneToOne oneToOne = pageField.getAnnotation(OneToOne.class);
        JoinColumn joinColumn = pageField.getAnnotation(JoinColumn.class);

        assertEquals(FetchType.LAZY, oneToOne.fetch());
        assertFalse(oneToOne.optional());
        assertTrue(oneToOne.mappedBy().isEmpty());
        assertEquals("page_id", joinColumn.name());
        assertTrue(joinColumn.unique());
        assertTrue(Arrays.stream(Page.class.getDeclaredFields())
                .noneMatch(field -> field.isAnnotationPresent(OneToOne.class)));
    }
}
