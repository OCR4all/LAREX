package de.uniwue.zpd.dachs.larex.backend.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationCollaborationDtoTest {
    @Test
    void serializesLeaseEpochAsADecimalStringWithoutLosingLongPrecision() {
        var mapper = JsonMapper.builder().build();
        var state = new AnnotationCollaborationDto.LeaseState(null, null, Long.MAX_VALUE, null);
        var json = mapper.writeValueAsString(state);
        assertEquals("\"9223372036854775807\"", mapper.readTree(json).get("leaseEpoch").toString());
    }

    @Test
    void deserializesSaveBeforeTakeoverWithoutLosingEpochPrecision() {
        var mapper = JsonMapper.builder().build();
        var payload = mapper.readValue("""
                {"force": true, "instanceId": "curator", "expectedLeaseEpoch": "9007199254740993",
                 "annotations": {"imageWidth": 100, "imageHeight": 100, "custom": "unsaved"}}
                """, AnnotationCollaborationDto.TakeoverRequestPayload.class);

        assertEquals(9007199254740993L, payload.expectedLeaseEpoch());
        assertEquals("unsaved", payload.annotations().custom());
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(payload).isEmpty());
            assertFalse(validator.validate(new AnnotationCollaborationDto.TakeoverRequestPayload(
                    false, "curator", payload.annotations(), payload.expectedLeaseEpoch())).isEmpty());
            assertFalse(validator.validate(new AnnotationCollaborationDto.TakeoverRequestPayload(
                    true, "curator", payload.annotations(), null)).isEmpty());
            assertTrue(validator.validate(new AnnotationCollaborationDto.TakeoverRequestPayload(
                    false, "viewer", null, null)).isEmpty());
        }
    }
}
