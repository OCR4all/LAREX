package de.uniwue.zpd.dachs.larex.backend.entity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ControlledDictionaryTest {

    @Test
    void setTagsCopiesImmutableInputIntoMutableSet() {
        ControlledDictionary dictionary = new ControlledDictionary();

        dictionary.setTags(Set.of("alpha", "beta"));

        assertDoesNotThrow(() -> dictionary.getTags().clear());
    }
}
