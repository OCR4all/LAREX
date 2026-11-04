package de.uniwue.zpd.dachs.larex.backend.service.workspace;

import de.uniwue.zpd.dachs.larex.backend.dto.WorkspaceDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.ControlledDictionary;
import de.uniwue.zpd.dachs.larex.backend.entity.LabelSet;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.NormalizationProfile;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.entity.ValidationRuleset;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceProjectDefaultsServiceTest {

    private static final String WORKSPACE_ID = "workspace-1";
    private static final String USER_ID = "user-1";

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private WorkspaceQueryService workspaceQueryService;
    @Mock
    private WorkspaceAccessService workspaceAccessService;

    private WorkspaceProjectDefaultsService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceProjectDefaultsService(projectRepository, workspaceQueryService, workspaceAccessService);
    }

    @Test
    void previewCountsUnsetAndExplicitProjectsSeparately() {
        TeamWorkspace workspace = workspace("old-label");
        Project unset = project("unset", null);
        Project explicit = project("explicit", labelSet("explicit-label"));
        Project locked = project("locked", null);
        locked.setLocked(true);
        when(workspaceQueryService.findWorkspaceById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(projectRepository.findByLibraryWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(unset, explicit, locked));

        WorkspaceDto.ProjectDefaultsPreviewResponse response = service.preview(
                WORKSPACE_ID,
                new WorkspaceDto.ProjectDefaultsProposal(
                        null, "new-label", null, null, null, null, 0, List.of(1)
                ),
                USER_ID
        );

        assertEquals(List.of(WorkspaceDto.ProjectDefaultKey.LABEL_SET), response.changedDefaults());
        assertEquals(1, response.unsetOnly().affectedProjects());
        assertEquals(1, response.unsetOnly().skippedLockedProjects());
        assertEquals(2, response.all().affectedProjects());
        assertEquals(1, response.all().skippedLockedProjects());
    }

    @Test
    void unsetOnlyPreservesExplicitValuesAndSkipsLockedProjects() {
        TeamWorkspace workspace = workspace("old-label");
        LabelSet replacement = labelSet("new-label");
        workspace.setLabelSet(replacement);
        WorkspaceProjectDefaultsService.WorkspaceDefaults before =
                WorkspaceProjectDefaultsService.WorkspaceDefaults.from(workspace("old-label"));

        Project unset = project("unset", null);
        Project explicit = project("explicit", labelSet("explicit-label"));
        Project locked = project("locked", null);
        locked.setLocked(true);
        when(projectRepository.findByLibraryWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(unset, explicit, locked));

        WorkspaceDto.ProjectDefaultsPropagationResult result = service.apply(
                WORKSPACE_ID,
                before,
                workspace,
                WorkspaceDto.ProjectDefaultPropagationScope.UNSET_ONLY,
                USER_ID
        );

        assertSame(replacement, unset.getLabelSet());
        assertEquals("explicit-label", explicit.getLabelSet().getId());
        assertEquals(1, result.updatedProjects());
        assertEquals(1, result.skippedLockedProjects());
        verify(projectRepository).save(unset);
        verify(projectRepository, never()).save(explicit);
        verify(projectRepository, never()).save(locked);
    }

    @Test
    void unsetOnlyKeepsExplicitValueWhenAnotherChangedDefaultIsUnset() {
        WorkspaceProjectDefaultsService.WorkspaceDefaults before =
                WorkspaceProjectDefaultsService.WorkspaceDefaults.from(workspace("old-label"));
        TeamWorkspace after = workspace(null);
        LabelSet newLabelSet = labelSet("new-label");
        after.setCodec(codec("new-codec"));
        after.setLabelSet(newLabelSet);

        Codec explicitCodec = codec("explicit-codec");
        Project project = project("project", null);
        project.setCodec(explicitCodec);
        when(projectRepository.findByLibraryWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(project));

        WorkspaceDto.ProjectDefaultsPropagationResult result = service.apply(
                WORKSPACE_ID, before, after, WorkspaceDto.ProjectDefaultPropagationScope.UNSET_ONLY, USER_ID);

        assertSame(explicitCodec, project.getCodec());
        assertSame(newLabelSet, project.getLabelSet());
        assertEquals(1, result.updatedProjects());
    }

    @Test
    void unsetOnlyDecidesEveryDefaultKeyPerProject() {
        WorkspaceProjectDefaultsService.WorkspaceDefaults before =
                WorkspaceProjectDefaultsService.WorkspaceDefaults.from(workspace(null));
        TeamWorkspace after = workspace(null);
        Codec newCodec = codec("new-codec");
        LabelSet newLabelSet = labelSet("new-label");
        ControlledDictionary newDictionary = dictionary("new-dictionary");
        TagSet newTagSet = tagSet("new-tag-set");
        NormalizationProfile newProfile = normalizationProfile("new-profile");
        ValidationRuleset newRuleset = validationRuleset("new-ruleset");
        after.setCodec(newCodec);
        after.setLabelSet(newLabelSet);
        after.setDictionary(newDictionary);
        after.setTagSet(newTagSet);
        after.setNormalizationProfile(newProfile);
        after.setValidationRuleset(newRuleset);
        after.setDefaultGtIndex(3);
        after.setDefaultRecognitionIndicesList(List.of(4));

        Codec explicitCodec = codec("explicit-codec");
        LabelSet explicitLabelSet = labelSet("explicit-label");
        ControlledDictionary explicitDictionary = dictionary("explicit-dictionary");
        TagSet explicitTagSet = tagSet("explicit-tag-set");
        NormalizationProfile explicitProfile = normalizationProfile("explicit-profile");
        ValidationRuleset explicitRuleset = validationRuleset("explicit-ruleset");
        Project explicitResources = project("explicit-resources", explicitLabelSet);
        explicitResources.setCodec(explicitCodec);
        explicitResources.setDictionary(explicitDictionary);
        explicitResources.setTagSet(explicitTagSet);
        explicitResources.setNormalizationProfile(explicitProfile);
        explicitResources.setValidationRuleset(explicitRuleset);

        Project explicitIndices = project("explicit-indices", null);
        explicitIndices.setDefaultGtIndex(1);
        explicitIndices.setDefaultRecognitionIndicesList(List.of(2));

        Project partialIndices = project("partial-indices", null);
        partialIndices.setDefaultGtIndex(1);

        when(projectRepository.findByLibraryWorkspaceId(WORKSPACE_ID))
                .thenReturn(List.of(explicitResources, explicitIndices, partialIndices));

        WorkspaceDto.ProjectDefaultsPropagationResult result = service.apply(
                WORKSPACE_ID, before, after, WorkspaceDto.ProjectDefaultPropagationScope.UNSET_ONLY, USER_ID);

        assertSame(explicitCodec, explicitResources.getCodec());
        assertSame(explicitLabelSet, explicitResources.getLabelSet());
        assertSame(explicitDictionary, explicitResources.getDictionary());
        assertSame(explicitTagSet, explicitResources.getTagSet());
        assertSame(explicitProfile, explicitResources.getNormalizationProfile());
        assertSame(explicitRuleset, explicitResources.getValidationRuleset());
        assertEquals(3, explicitResources.getDefaultGtIndex());
        assertEquals(List.of(4), explicitResources.getDefaultRecognitionIndicesList());

        assertSame(newCodec, explicitIndices.getCodec());
        assertSame(newLabelSet, explicitIndices.getLabelSet());
        assertSame(newDictionary, explicitIndices.getDictionary());
        assertSame(newTagSet, explicitIndices.getTagSet());
        assertSame(newProfile, explicitIndices.getNormalizationProfile());
        assertSame(newRuleset, explicitIndices.getValidationRuleset());
        assertEquals(1, explicitIndices.getDefaultGtIndex());
        assertEquals(List.of(2), explicitIndices.getDefaultRecognitionIndicesList());

        assertSame(newCodec, partialIndices.getCodec());
        assertEquals(1, partialIndices.getDefaultGtIndex());
        assertEquals(null, partialIndices.getDefaultRecognitionIndices());

        assertEquals(3, result.updatedProjects());
    }

    @Test
    void allClearsExistingValuesAndUpdatesTextIndicesAsPair() {
        TeamWorkspace beforeWorkspace = workspace("old-label");
        beforeWorkspace.setDefaultGtIndex(1);
        beforeWorkspace.setDefaultRecognitionIndicesList(List.of(2));
        WorkspaceProjectDefaultsService.WorkspaceDefaults before =
                WorkspaceProjectDefaultsService.WorkspaceDefaults.from(beforeWorkspace);

        TeamWorkspace afterWorkspace = workspace(null);
        afterWorkspace.setDefaultGtIndex(3);
        afterWorkspace.setDefaultRecognitionIndicesList(List.of(4));
        Project project = project("project", labelSet("old-label"));
        project.setDefaultGtIndex(0);
        project.setDefaultRecognitionIndicesList(List.of(1));
        when(projectRepository.findByLibraryWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(project));

        WorkspaceDto.ProjectDefaultsPropagationResult result = service.apply(
                WORKSPACE_ID,
                before,
                afterWorkspace,
                WorkspaceDto.ProjectDefaultPropagationScope.ALL,
                USER_ID
        );

        assertEquals(null, project.getLabelSet());
        assertEquals(3, project.getDefaultGtIndex());
        assertEquals(List.of(4), project.getDefaultRecognitionIndicesList());
        assertEquals(1, result.updatedProjects());
    }

    @Test
    void previewRequiresPresetPermission() {
        org.mockito.Mockito.doThrow(new SecurityException("denied"))
                .when(workspaceAccessService).requireSetPresetsAccess(WORKSPACE_ID, USER_ID);

        assertThrows(SecurityException.class, () -> service.preview(
                WORKSPACE_ID,
                new WorkspaceDto.ProjectDefaultsProposal(null, null, null, null, null, null, 0, List.of(1)),
                USER_ID
        ));
        verify(projectRepository, never()).findByLibraryWorkspaceId(WORKSPACE_ID);
    }

    private TeamWorkspace workspace(String labelSetId) {
        TeamWorkspace workspace = new TeamWorkspace("Workspace", "description", USER_ID);
        workspace.setId(WORKSPACE_ID);
        workspace.setDefaultGtIndex(0);
        workspace.setDefaultRecognitionIndicesList(List.of(1));
        if (labelSetId != null) workspace.setLabelSet(labelSet(labelSetId));
        return workspace;
    }

    private Project project(String name, LabelSet labelSet) {
        Library library = new Library(WORKSPACE_ID, "Library");
        library.setId("library-1");
        Project project = new Project(name, "description", library);
        project.setLabelSet(labelSet);
        return project;
    }

    private LabelSet labelSet(String id) {
        LabelSet labelSet = new LabelSet();
        labelSet.setId(id);
        labelSet.setName(id);
        return labelSet;
    }

    private Codec codec(String id) {
        Codec codec = new Codec();
        codec.setId(id);
        return codec;
    }

    private ControlledDictionary dictionary(String id) {
        ControlledDictionary dictionary = new ControlledDictionary();
        dictionary.setId(id);
        return dictionary;
    }

    private TagSet tagSet(String id) {
        TagSet tagSet = new TagSet();
        tagSet.setId(id);
        return tagSet;
    }

    private NormalizationProfile normalizationProfile(String id) {
        NormalizationProfile profile = new NormalizationProfile();
        profile.setId(id);
        return profile;
    }

    private ValidationRuleset validationRuleset(String id) {
        ValidationRuleset ruleset = new ValidationRuleset();
        ruleset.setId(id);
        return ruleset;
    }
}
