package de.uniwue.zpd.dachs.larex.backend.controller.user;

import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class UserControllerTest {
    private final UserService userService = mock(UserService.class);
    private final UserController controller = new UserController(userService);

    @Test
    void resolvesUniqueCreatorIdsToUsernamesAndOmitsMissingUsers() {
        UserDto user = mock(UserDto.class);
        when(user.id()).thenReturn("creator");
        when(user.username()).thenReturn("alice");
        when(userService.getUsersByIds(List.of("creator", "deleted"))).thenReturn(Map.of("creator", user));

        assertThat(controller.getUserLabels(List.of("creator", "creator", "deleted")).getBody())
                .containsExactly(Map.entry("creator", "alice"));
        verify(userService).getUsersByIds(List.of("creator", "deleted"));
    }

    @Test
    void rejectsOversizedBatchesWithoutLookingUpUsers() {
        assertThatThrownBy(() -> controller.getUserLabels(Collections.nCopies(101, "creator")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(userService);
    }
}
