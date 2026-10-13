package de.uniwue.zpd.dachs.larex.backend.controller.user;

import de.uniwue.zpd.dachs.larex.backend.dto.UserDto;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers() {
        List<UserDto> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    /** Resolve only the public labels needed to identify job creators. */
    @GetMapping("/labels")
    public ResponseEntity<Map<String, String>> getUserLabels(@RequestParam("ids") List<String> ids) {
        if (ids.size() > 100) {
            throw new IllegalArgumentException("At most 100 user IDs may be resolved at once");
        }
        return ResponseEntity.ok(userService.getUsersByIds(ids.stream().distinct().toList()).values().stream()
                .filter(user -> user.username() != null)
                .collect(Collectors.toMap(UserDto::id, UserDto::username)));
    }

    /**
     * Search users by username or email for inviting to workspaces
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserDto>> searchUsers(
            @RequestParam(value = "q", defaultValue = "") String query,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        List<UserDto> users = userService.searchUsers(query, Math.min(limit, 50));
        return ResponseEntity.ok(users);
    }
}
