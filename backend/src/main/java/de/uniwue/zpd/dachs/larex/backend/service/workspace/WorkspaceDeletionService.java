package de.uniwue.zpd.dachs.larex.backend.service.workspace;

import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.version.PageXmlVersionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
@Transactional
public class WorkspaceDeletionService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceDeletionService.class);

    private static final String PROJECTS = "SELECT p.id FROM projects p JOIN libraries l ON l.id = p.library_id WHERE l.workspace_id = :ws";
    private static final String PAGES = "SELECT id FROM pages WHERE project_id IN (" + PROJECTS + ")";
    private static final String TASKS = "SELECT id FROM tasks WHERE workspace_id = :ws";
    private static final String RUNS = "SELECT id FROM action_runs WHERE workspace_id = :ws";
    private static final String DATASETS = "SELECT id FROM datasets WHERE workspace_id = :ws";
    private static final String DATASET_ITEMS = "SELECT id FROM dataset_items WHERE dataset_id IN (" + DATASETS + ")";
    private static final String LINKED_ITEMS = "SELECT id FROM dataset_items WHERE mode = 'LINK' AND source_page_id IN (" + PAGES + ")";
    private static final String WORKFLOW_DEFINITIONS = "SELECT id FROM action_processor_definitions WHERE workflow_id IN "
            + "(SELECT id FROM action_workflows WHERE workspace_id = :ws)";
    private static final String LIBRARIES = "SELECT id FROM libraries WHERE workspace_id = :ws";

    private static final List<String> PURGE = List.of(
            "DELETE FROM task_comment_mentions WHERE comment_id IN (SELECT id FROM task_comments WHERE task_id IN (" + TASKS + "))",
            "DELETE FROM task_comments WHERE task_id IN (" + TASKS + ")",
            "DELETE FROM task_assignees WHERE task_id IN (" + TASKS + ")",
            "DELETE FROM task_activity_logs WHERE task_id IN (" + TASKS + ")",
            "DELETE FROM task_reminders WHERE task_id IN (" + TASKS + ")",
            "DELETE FROM subtasks WHERE task_id IN (" + TASKS + ") OR page_id IN (" + PAGES + ")",
            "DELETE FROM task_page_links WHERE task_id IN (" + TASKS + ") OR page_id IN (" + PAGES + ")",
            "DELETE FROM task_project_links WHERE task_id IN (" + TASKS + ") OR project_id IN (" + PROJECTS + ")",
            "DELETE FROM tasks WHERE workspace_id = :ws",

            "DELETE FROM action_run_dismissals WHERE run_id IN (" + RUNS + ")",
            "DELETE FROM action_run_log_events WHERE run_id IN (" + RUNS + ")",
            "DELETE FROM action_runs WHERE workspace_id = :ws",
            "DELETE FROM action_workflow_runs WHERE workspace_id = :ws",
            "DELETE FROM action_outputs WHERE workspace_id = :ws OR project_id IN (" + PROJECTS + ") OR dataset_id IN (" + DATASETS + ")",
            "DELETE FROM action_processor_assignments WHERE workspace_id = :ws OR processor_definition_id IN (" + WORKFLOW_DEFINITIONS + ")",
            "DELETE FROM action_processor_workspace_availability WHERE workspace_id = :ws OR processor_definition_id IN (" + WORKFLOW_DEFINITIONS + ")",
            "DELETE FROM action_workspace_execute_roles WHERE workspace_id = :ws OR processor_definition_id IN (" + WORKFLOW_DEFINITIONS + ")",
            "DELETE FROM action_processor_definitions WHERE id IN (" + WORKFLOW_DEFINITIONS + ")",
            "DELETE FROM action_workflows WHERE workspace_id = :ws",

            "DELETE FROM dataset_item_source_images WHERE dataset_item_id IN (" + DATASET_ITEMS + ")",
            "DELETE FROM dataset_item_tags WHERE dataset_item_id IN (" + DATASET_ITEMS + ")",
            "DELETE FROM dataset_stratify_tags WHERE dataset_id IN (" + DATASETS + ")",
            "DELETE FROM dataset_tags WHERE dataset_id IN (" + DATASETS + ")",
            "DELETE FROM datasets WHERE workspace_id = :ws",

            "DELETE FROM upload_session_files WHERE session_id IN (SELECT id FROM upload_sessions WHERE workspace_id = :ws)",
            "DELETE FROM upload_sessions WHERE workspace_id = :ws",
            "DELETE FROM import_jobs WHERE workspace_id = :ws",
            "DELETE FROM iiif_import_jobs WHERE workspace_id = :ws",
            "DELETE FROM project_export_jobs WHERE workspace_id = :ws",
            "DELETE FROM project_transfer_requests WHERE source_workspace_id = :ws OR target_workspace_id = :ws OR project_id IN (" + PROJECTS + ")",
            "DELETE FROM resource_transfer_requests WHERE source_workspace_id = :ws OR target_workspace_id = :ws",
            "DELETE FROM search_lexicon_entries WHERE workspace_id = :ws OR project_id IN (" + PROJECTS + ")",

            "DELETE FROM dataset_item_source_images WHERE dataset_item_id IN (" + LINKED_ITEMS + ")",
            "DELETE FROM dataset_item_tags WHERE dataset_item_id IN (" + LINKED_ITEMS + ")",
            "DELETE FROM dataset_items WHERE id IN (" + LINKED_ITEMS + ")",
            "DELETE FROM page_tags WHERE page_id IN (" + PAGES + ")",
            "DELETE FROM pages WHERE project_id IN (" + PROJECTS + ")",
            "DELETE FROM project_tags WHERE project_id IN (" + PROJECTS + ")",
            "DELETE FROM projects WHERE id IN (" + PROJECTS + ")",
            "DELETE FROM stored_files WHERE workspace_id = :ws",

            "DELETE FROM workspace_members WHERE workspace_id = :ws",
            "DELETE FROM workspace_storage_quotas WHERE workspace_id = :ws",
            "DELETE FROM team_workspaces WHERE id = :ws",

            "DELETE FROM codec_characters WHERE codec_id IN (SELECT id FROM codecs WHERE library_id IN (" + LIBRARIES + "))",
            "DELETE FROM codec_tags WHERE codec_id IN (SELECT id FROM codecs WHERE library_id IN (" + LIBRARIES + "))",
            "DELETE FROM codecs WHERE library_id IN (" + LIBRARIES + ")",
            "DELETE FROM controlled_dictionary_entries WHERE dictionary_id IN (SELECT id FROM controlled_dictionaries WHERE library_id IN (" + LIBRARIES + "))",
            "DELETE FROM controlled_dictionary_tags WHERE dictionary_id IN (SELECT id FROM controlled_dictionaries WHERE library_id IN (" + LIBRARIES + "))",
            "DELETE FROM controlled_dictionaries WHERE library_id IN (" + LIBRARIES + ")",
            "DELETE FROM label_set_tags WHERE label_set_id IN (SELECT id FROM label_sets WHERE workspace_id = :ws)",
            "DELETE FROM label_sets WHERE workspace_id = :ws",
            "DELETE FROM tag_set_meta_tags WHERE tag_set_id IN (SELECT id FROM tag_sets WHERE workspace_id = :ws)",
            "DELETE FROM tag_sets WHERE workspace_id = :ws",
            "DELETE FROM normalization_profile_replacement_rules WHERE normalization_profile_id IN (SELECT id FROM normalization_profiles WHERE workspace_id = :ws)",
            "DELETE FROM normalization_profile_tags WHERE normalization_profile_id IN (SELECT id FROM normalization_profiles WHERE workspace_id = :ws)",
            "DELETE FROM normalization_profiles WHERE workspace_id = :ws",
            "DELETE FROM validation_ruleset_tags WHERE validation_ruleset_id IN (SELECT id FROM validation_rulesets WHERE workspace_id = :ws)",
            "DELETE FROM validation_rulesets WHERE workspace_id = :ws",
            "DELETE FROM keyboard_items WHERE virtual_keyboard_id IN (SELECT id FROM virtual_keyboards WHERE workspace_id = :ws)",
            "DELETE FROM virtual_keyboard_tags WHERE virtual_keyboard_id IN (SELECT id FROM virtual_keyboards WHERE workspace_id = :ws)",
            "DELETE FROM virtual_keyboards WHERE workspace_id = :ws",
            "DELETE FROM libraries WHERE workspace_id = :ws"
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final HierarchicalFileStorageService storage;
    private final PageXmlVersionService pageXmlVersionService;

    public WorkspaceDeletionService(NamedParameterJdbcTemplate jdbc,
                                    HierarchicalFileStorageService storage,
                                    PageXmlVersionService pageXmlVersionService) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.pageXmlVersionService = pageXmlVersionService;
    }

    public void deleteTeamWorkspace(String workspaceId) {
        MapSqlParameterSource params = new MapSqlParameterSource("ws", workspaceId)
                .addValue("terminal", ActionRun.Status.terminalStatuses().stream().map(Enum::name).toList());
        Integer activeRuns = jdbc.queryForObject(
                "SELECT count(*) FROM action_runs WHERE workspace_id = :ws AND status NOT IN (:terminal)", params, Integer.class);
        Integer activeWorkflowRuns = jdbc.queryForObject(
                "SELECT count(*) FROM action_workflow_runs WHERE workspace_id = :ws AND status IN ('RUNNING', 'CANCEL_REQUESTED')", params, Integer.class);
        if ((activeRuns != null && activeRuns > 0) || (activeWorkflowRuns != null && activeWorkflowRuns > 0)) {
            throw new IllegalStateException("Workspace has active LAREX Actions and cannot be deleted");
        }

        List<String> xmlIds = jdbc.queryForList(
                "SELECT id FROM page_xmls WHERE page_id IN (" + PAGES + ")", params, String.class);
        List<String> thumbnailPaths = jdbc.queryForList(
                "SELECT thumbnail_path FROM page_images WHERE thumbnail_path IS NOT NULL AND page_id IN (" + PAGES + ")", params, String.class);

        for (String statement : PURGE) {
            jdbc.update(statement, params);
        }

        List<Path> trees = new ArrayList<>();
        trees.add(storage.resolveUploadPath(storage.workspaceRootRelativePath(workspaceId)));
        trees.add(storage.resolveUploadPath(storage.projectReleasesRootRelativePath(workspaceId)));
        afterCommit(() -> deleteFiles(trees, xmlIds, thumbnailPaths));
    }

    private void deleteFiles(List<Path> trees, List<String> xmlIds, List<String> thumbnailPaths) {
        trees.forEach(this::deleteTree);
        pageXmlVersionService.deleteVersionDirectories(xmlIds);
        for (String thumbnailPath : thumbnailPaths) {
            try {
                Files.deleteIfExists(storage.resolveUploadPath(thumbnailPath));
            } catch (IOException | IllegalArgumentException e) {
                log.warn("Could not delete thumbnail {} of deleted workspace: {}", thumbnailPath, e.getMessage());
            }
        }
    }

    private void deleteTree(Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("Could not delete directory {} of deleted workspace", root, e);
        }
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
