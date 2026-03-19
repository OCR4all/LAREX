package de.uniwue.zpd.dachs.larex.backend.service.search;

import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.SearchLexiconEntry;
import de.uniwue.zpd.dachs.larex.backend.repository.page.PageTextContentRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.search.SearchLexiconEntryRepository;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchLexiconService {

    private static final Logger log = LoggerFactory.getLogger(SearchLexiconService.class);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*");

    private final SearchLexiconEntryRepository searchLexiconEntryRepository;
    private final ProjectRepository projectRepository;
    private final PageTextContentRepository pageTextContentRepository;

    public SearchLexiconService(SearchLexiconEntryRepository searchLexiconEntryRepository,
                                ProjectRepository projectRepository,
                                PageTextContentRepository pageTextContentRepository) {
        this.searchLexiconEntryRepository = searchLexiconEntryRepository;
        this.projectRepository = projectRepository;
        this.pageTextContentRepository = pageTextContentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rebuildProjectLexicon(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return;
        }

        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null || project.getLibrary() == null) {
            return;
        }

        String workspaceId = project.getLibrary().getWorkspaceId();
        List<String> texts = pageTextContentRepository.findPrimaryTextContentsByProjectId(projectId);

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }

            Matcher matcher = TOKEN_PATTERN.matcher(text);
            while (matcher.find()) {
                String token = normalizeToken(matcher.group());
                if (token == null) {
                    continue;
                }
                counts.merge(token, 1, Integer::sum);
            }
        }

        searchLexiconEntryRepository.deleteByProjectId(projectId);
        if (counts.isEmpty()) {
            return;
        }

        List<SearchLexiconEntry> entries = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            entries.add(new SearchLexiconEntry(workspaceId, projectId, entry.getKey(), entry.getValue()));
        }
        searchLexiconEntryRepository.saveAll(entries);
        log.debug("Rebuilt search lexicon for project {} with {} tokens", projectId, entries.size());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureWorkspaceLexicon(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return;
        }
        if (searchLexiconEntryRepository.countByWorkspaceId(workspaceId) > 0) {
            return;
        }

        for (Project project : projectRepository.findByLibraryWorkspaceId(workspaceId)) {
            rebuildProjectLexicon(project.getId());
        }
    }

    private String normalizeToken(String value) {
        if (value == null) {
            return null;
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .trim();
        return normalized.isBlank() ? null : normalized;
    }
}
