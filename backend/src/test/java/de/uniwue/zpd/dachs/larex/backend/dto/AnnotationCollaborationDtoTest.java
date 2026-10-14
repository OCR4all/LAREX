package de.uniwue.zpd.dachs.larex.backend.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnotationCollaborationDtoTest {
    @Test
    void serializesLeaseEpochAsADecimalStringWithoutLosingLongPrecision() {
        var mapper = JsonMapper.builder().build();
        var state = new AnnotationCollaborationDto.LeaseState(null, null, Long.MAX_VALUE, null);
        var json = mapper.writeValueAsString(state);
        assertEquals("\"9223372036854775807\"", mapper.readTree(json).get("leaseEpoch").toString());
    }
}
