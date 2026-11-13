package de.uniwue.zpd.dachs.larex.backend.service.workspace;

import de.uniwue.zpd.dachs.larex.backend.entity.Codec;
import de.uniwue.zpd.dachs.larex.backend.entity.Library;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.PersonalWorkspace;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.validation.ValidationRulesetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceMemberRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.label.LabelSetInitializationService;
import de.uniwue.zpd.dachs.larex.backend.service.notification.NotificationService;
import de.uniwue.zpd.dachs.larex.backend.service.user.UserService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkspaceDefaultResourceScopeTest {

    private final PersonalWorkspaceRepository personalWorkspaces = mock(PersonalWorkspaceRepository.class);
    private final TeamWorkspaceRepository teamWorkspaces = mock(TeamWorkspaceRepository.class);
    private final CodecRepository codecs = mock(CodecRepository.class);
    private final LabelSetRepository labelSets = mock(LabelSetRepository.class);
    private final WorkspaceAccessService workspaceAccess = mock(WorkspaceAccessService.class);

    @Test
    void personalWorkspaceRejectsCodecFromAnotherWorkspace() {
        PersonalWorkspace own = personalWorkspace();
        Codec foreign = codec("foreign-codec", "victim-workspace");
        when(codecs.findById("foreign-codec")).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> personalService().updatePersonalWorkspace(own.getId(), "description", null,
                "foreign-codec", null, null, null, null, null, null, null, null, null, null, null, "owner"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(personalWorkspaces, never()).save(any());
    }

    @Test
    void personalWorkspaceAcceptsCodecFromItsOwnLibrary() {
        PersonalWorkspace own = personalWorkspace();
        Codec codec = codec("own-codec", own.getId());
        when(codecs.findByIdAndLibraryWorkspaceId("own-codec", own.getId())).thenReturn(Optional.of(codec));
        when(personalWorkspaces.save(own)).thenReturn(own);

        var result = personalService().updatePersonalWorkspace(own.getId(), "description", null,
                "own-codec", null, null, null, null, null, null, null, null, null, null, null, "owner");

        assertThat(result.orElseThrow().getCodec()).isSameAs(codec);
    }

    @Test
    void teamWorkspaceRejectsLabelSetFromAnotherWorkspace() {
        TeamWorkspace team = new TeamWorkspace("Team", "description", "owner");
        team.setId("team-workspace");
        when(teamWorkspaces.findById("team-workspace")).thenReturn(Optional.of(team));
        when(workspaceAccess.isUserAdministrator("team-workspace", "owner")).thenReturn(true);

        assertThatThrownBy(() -> teamService().updateTeamWorkspace("team-workspace", "Team", "description", null,
                null, "foreign-label-set", null, null, null, null, null, null, null, null, null, null, "owner"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(teamWorkspaces, never()).save(any());
    }

    private PersonalWorkspace personalWorkspace() {
        PersonalWorkspace workspace = new PersonalWorkspace("owner");
        workspace.setId("own-workspace");
        when(personalWorkspaces.findById("own-workspace")).thenReturn(Optional.of(workspace));
        return workspace;
    }

    private static Codec codec(String id, String workspaceId) {
        Codec codec = new Codec(id, new Library(workspaceId, "library"));
        codec.setId(id);
        return codec;
    }

    private PersonalWorkspaceService personalService() {
        return new PersonalWorkspaceService(personalWorkspaces, mock(LibraryRepository.class),
                mock(WorkspaceMemberRepository.class), mock(WorkspaceQueryService.class), codecs,
                mock(ControlledDictionaryRepository.class), labelSets, mock(TagSetRepository.class),
                mock(NormalizationProfileRepository.class), mock(ValidationRulesetRepository.class),
                mock(LabelSetInitializationService.class));
    }

    private TeamWorkspaceService teamService() {
        return new TeamWorkspaceService(teamWorkspaces, mock(LibraryRepository.class),
                mock(WorkspaceMemberRepository.class), mock(NotificationService.class), mock(UserService.class),
                mock(WorkspaceQueryService.class), codecs, mock(ControlledDictionaryRepository.class), labelSets,
                mock(TagSetRepository.class), mock(NormalizationProfileRepository.class),
                mock(ValidationRulesetRepository.class), mock(LabelSetInitializationService.class), workspaceAccess);
    }
}
