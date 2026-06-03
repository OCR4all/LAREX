package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.service.action.ActionRunService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/actions/runs")
public class ActionAdminRunController {

    private final ActionRunService actionRunService;

    public ActionAdminRunController(ActionRunService actionRunService) {
        this.actionRunService = actionRunService;
    }

    @GetMapping
    public ResponseEntity<List<ActionDto.AdminRunResponse>> listRuns() {
        return ResponseEntity.ok(actionRunService.listAllAdminRuns());
    }
}
