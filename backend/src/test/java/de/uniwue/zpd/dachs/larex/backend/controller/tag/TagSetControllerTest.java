package de.uniwue.zpd.dachs.larex.backend.controller.tag;

import de.uniwue.zpd.dachs.larex.backend.dto.AuthorizationCapabilitiesDto;
import de.uniwue.zpd.dachs.larex.backend.dto.TagSetDto;
import de.uniwue.zpd.dachs.larex.backend.service.tag.TagSetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TagSetControllerTest {

    @Mock
    private TagSetService tagSetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TagSetController(tagSetService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    void createTagSet_bindsRequestBodyToDto() throws Exception {
        when(tagSetService.createTagSet(
                nullable(String.class),
                eq("ws-1"),
                any(TagSetDto.CreateOrUpdateRequest.class)
        )).thenReturn(new TagSetDto.Response(
                "tag-set-1",
                new TagSetDto.Meta("People", "Named entities", List.of("ner")),
                List.of(new TagSetDto.TagNode("person", "Person", null, "#00AA88", List.of())),
                1,
                LocalDateTime.parse("2026-06-16T16:00:00"),
                LocalDateTime.parse("2026-06-16T16:00:00"),
                new AuthorizationCapabilitiesDto.ResourceCapabilities(true, true, true)
        ));

        mockMvc.perform(post("/workspaces/ws-1/tag-sets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "meta": {
                                    "name": "People",
                                    "description": "Named entities",
                                    "tags": ["ner"]
                                  },
                                  "tags": [
                                    {
                                      "id": "person",
                                      "title": "Person",
                                      "description": null,
                                      "color": "#00AA88",
                                      "children": []
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("tag-set-1"));

        ArgumentCaptor<TagSetDto.CreateOrUpdateRequest> requestCaptor =
                ArgumentCaptor.forClass(TagSetDto.CreateOrUpdateRequest.class);
        verify(tagSetService).createTagSet(nullable(String.class), eq("ws-1"), requestCaptor.capture());

        TagSetDto.CreateOrUpdateRequest request = requestCaptor.getValue();
        assertEquals("People", request.meta().name());
        assertEquals("person", request.tags().getFirst().id());
        assertEquals("Person", request.tags().getFirst().title());
    }
}
