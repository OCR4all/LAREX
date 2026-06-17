package de.uniwue.zpd.dachs.larex.backend.service.project;

import tools.jackson.databind.node.JsonNodeFactory;
import de.uniwue.zpd.dachs.larex.backend.entity.KeyboardItem;
import de.uniwue.zpd.dachs.larex.backend.entity.ResourceTransferRequest;
import de.uniwue.zpd.dachs.larex.backend.entity.TagSet;
import de.uniwue.zpd.dachs.larex.backend.entity.VirtualKeyboard;
import de.uniwue.zpd.dachs.larex.backend.entity.workspace.TeamWorkspace;
import de.uniwue.zpd.dachs.larex.backend.repository.codec.CodecRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.dictionary.ControlledDictionaryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.keyboard.VirtualKeyboardRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.label.LabelSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.library.LibraryRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.normalization.NormalizationProfileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ResourceTransferRequestRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.tag.TagSetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.validation.ValidationRulesetRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.PersonalWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.TeamWorkspaceRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.workspace.WorkspaceQueryService;
import de.uniwue.zpd.dachs.larex.backend.service.security.AuthorizationPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceTransferServiceTest {

    @Mock
    private ResourceTransferRequestRepository transferRequestRepository;

    @Mock
    private CodecRepository codecRepository;

    @Mock
    private ControlledDictionaryRepository dictionaryRepository;

    @Mock
    private VirtualKeyboardRepository virtualKeyboardRepository;

    @Mock
    private LabelSetRepository labelSetRepository;

    @Mock
    private TagSetRepository tagSetRepository;

    @Mock
    private NormalizationProfileRepository normalizationProfileRepository;

    @Mock
    private ValidationRulesetRepository validationRulesetRepository;

    @Mock
    private LibraryRepository libraryRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private PersonalWorkspaceRepository personalWorkspaceRepository;

    @Mock
    private TeamWorkspaceRepository teamWorkspaceRepository;

    @Mock
    private WorkspaceQueryService workspaceQueryService;

    @Mock
    private AuthorizationPolicyService authorizationPolicyService;

    private ResourceTransferService service;

    @BeforeEach
    void setUp() {
        service = new ResourceTransferService(
                transferRequestRepository,
                codecRepository,
                dictionaryRepository,
                virtualKeyboardRepository,
                labelSetRepository,
                tagSetRepository,
                normalizationProfileRepository,
                validationRulesetRepository,
                libraryRepository,
                projectRepository,
                personalWorkspaceRepository,
                teamWorkspaceRepository,
                workspaceQueryService,
                authorizationPolicyService
        );
    }

    @Test
    void requestTransfer_virtualKeyboardCopy_autoApprovesAndCopiesLayout() {
        String sourceWorkspaceId = "ws-source";
        String targetWorkspaceId = "ws-target";
        String userId = "user-1";
        VirtualKeyboard keyboard = keyboard("keyboard-1", sourceWorkspaceId);
        KeyboardItem item = item();
        keyboard.addItem(item);

        when(virtualKeyboardRepository.findById("keyboard-1")).thenReturn(Optional.of(keyboard));
        when(authorizationPolicyService.canManageToolkit(sourceWorkspaceId, userId)).thenReturn(true);
        when(authorizationPolicyService.canManageToolkit(targetWorkspaceId, userId)).thenReturn(true);
        when(workspaceQueryService.findWorkspaceById(targetWorkspaceId)).thenReturn(Optional.of(workspace(targetWorkspaceId)));
        when(transferRequestRepository.existsByResourceIdAndResourceTypeAndStatus(
                "keyboard-1",
                ResourceTransferRequest.ResourceType.VIRTUAL_KEYBOARD,
                ResourceTransferRequest.Status.PENDING
        )).thenReturn(false);
        when(transferRequestRepository.save(any(ResourceTransferRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(virtualKeyboardRepository.save(any(VirtualKeyboard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<ResourceTransferRequest> result = service.requestTransfer(
                "keyboard-1",
                ResourceTransferRequest.ResourceType.VIRTUAL_KEYBOARD,
                targetWorkspaceId,
                userId,
                "Please copy",
                ResourceTransferRequest.TransferType.COPY
        );

        assertTrue(result.isPresent());
        assertEquals(ResourceTransferRequest.Status.COMPLETED, result.get().getStatus());

        ArgumentCaptor<VirtualKeyboard> captor = ArgumentCaptor.forClass(VirtualKeyboard.class);
        verify(virtualKeyboardRepository, times(2)).save(captor.capture());
        VirtualKeyboard copy = captor.getAllValues().getLast();
        assertEquals("Special Keyboard (Copy)", copy.getName());
        assertEquals(targetWorkspaceId, copy.getWorkspaceId());
        assertEquals("Useful characters", copy.getDescription());
        assertEquals(List.of("latin", "transcription"), copy.getTags());
        assertEquals(12, copy.getCols());
        assertEquals(4, copy.getRows());
        assertEquals(1, copy.getItems().size());

        KeyboardItem copiedItem = copy.getItems().getFirst();
        assertNotSame(item, copiedItem);
        assertEquals(item.getCharValue(), copiedItem.getCharValue());
        assertEquals(item.getShiftChar(), copiedItem.getShiftChar());
        assertEquals(item.getX(), copiedItem.getX());
        assertEquals(item.getY(), copiedItem.getY());
        assertEquals(item.getW(), copiedItem.getW());
        assertEquals(item.getColorClass(), copiedItem.getColorClass());
        assertEquals(item.getTextClass(), copiedItem.getTextClass());
        assertEquals(item.getDescription(), copiedItem.getDescription());
        assertEquals(item.getShiftDescription(), copiedItem.getShiftDescription());
        assertEquals(copy, copiedItem.getVirtualKeyboard());
        assertEquals(sourceWorkspaceId, keyboard.getWorkspaceId());
    }

    @Test
    void requestTransfer_virtualKeyboardMove_autoApprovesAndMovesExistingKeyboard() {
        String sourceWorkspaceId = "ws-source";
        String targetWorkspaceId = "ws-target";
        String userId = "user-1";
        VirtualKeyboard keyboard = keyboard("keyboard-1", sourceWorkspaceId);

        when(virtualKeyboardRepository.findById("keyboard-1")).thenReturn(Optional.of(keyboard));
        when(authorizationPolicyService.canManageToolkit(sourceWorkspaceId, userId)).thenReturn(true);
        when(authorizationPolicyService.canManageToolkit(targetWorkspaceId, userId)).thenReturn(true);
        when(workspaceQueryService.findWorkspaceById(targetWorkspaceId)).thenReturn(Optional.of(workspace(targetWorkspaceId)));
        when(transferRequestRepository.existsByResourceIdAndResourceTypeAndStatus(
                "keyboard-1",
                ResourceTransferRequest.ResourceType.VIRTUAL_KEYBOARD,
                ResourceTransferRequest.Status.PENDING
        )).thenReturn(false);
        when(transferRequestRepository.save(any(ResourceTransferRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(virtualKeyboardRepository.save(any(VirtualKeyboard.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<ResourceTransferRequest> result = service.requestTransfer(
                "keyboard-1",
                ResourceTransferRequest.ResourceType.VIRTUAL_KEYBOARD,
                targetWorkspaceId,
                userId,
                null,
                ResourceTransferRequest.TransferType.MOVE
        );

        assertTrue(result.isPresent());
        assertEquals(ResourceTransferRequest.Status.COMPLETED, result.get().getStatus());
        assertEquals(targetWorkspaceId, keyboard.getWorkspaceId());
        verify(virtualKeyboardRepository).save(keyboard);
    }

    @Test
    void requestTransfer_tagSetCopy_autoApprovesAndCopiesDefinition() {
        String sourceWorkspaceId = "ws-source";
        String targetWorkspaceId = "ws-target";
        String userId = "user-1";
        TagSet tagSet = new TagSet(
                sourceWorkspaceId,
                "Editorial Tags",
                "Reusable taxonomy",
                JsonNodeFactory.instance.objectNode().put("root", true)
        );
        tagSet.setId("tag-set-1");
        tagSet.setTags(List.of("editorial", "review"));

        when(tagSetRepository.findById("tag-set-1")).thenReturn(Optional.of(tagSet));
        when(authorizationPolicyService.canManageToolkit(sourceWorkspaceId, userId)).thenReturn(true);
        when(authorizationPolicyService.canManageToolkit(targetWorkspaceId, userId)).thenReturn(true);
        when(workspaceQueryService.findWorkspaceById(targetWorkspaceId)).thenReturn(Optional.of(workspace(targetWorkspaceId)));
        when(transferRequestRepository.existsByResourceIdAndResourceTypeAndStatus(
                "tag-set-1",
                ResourceTransferRequest.ResourceType.TAG_SET,
                ResourceTransferRequest.Status.PENDING
        )).thenReturn(false);
        when(transferRequestRepository.save(any(ResourceTransferRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagSetRepository.save(any(TagSet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<ResourceTransferRequest> result = service.requestTransfer(
                "tag-set-1",
                ResourceTransferRequest.ResourceType.TAG_SET,
                targetWorkspaceId,
                userId,
                "Please copy",
                ResourceTransferRequest.TransferType.COPY
        );

        assertTrue(result.isPresent());
        assertEquals(ResourceTransferRequest.Status.COMPLETED, result.get().getStatus());

        ArgumentCaptor<TagSet> captor = ArgumentCaptor.forClass(TagSet.class);
        verify(tagSetRepository).save(captor.capture());
        TagSet copy = captor.getValue();
        assertEquals("Editorial Tags (Copy)", copy.getName());
        assertEquals(targetWorkspaceId, copy.getWorkspaceId());
        assertEquals("Reusable taxonomy", copy.getDescription());
        assertEquals(List.of("editorial", "review"), copy.getTags());
        assertEquals(tagSet.getDefinition(), copy.getDefinition());
        assertEquals(sourceWorkspaceId, tagSet.getWorkspaceId());
    }

    private static VirtualKeyboard keyboard(String id, String workspaceId) {
        VirtualKeyboard keyboard = new VirtualKeyboard();
        keyboard.setId(id);
        keyboard.setName("Special Keyboard");
        keyboard.setWorkspaceId(workspaceId);
        keyboard.setDescription("Useful characters");
        keyboard.setCols(12);
        keyboard.setRows(4);
        keyboard.setTags(List.of("latin", "transcription"));
        return keyboard;
    }

    private static KeyboardItem item() {
        KeyboardItem item = new KeyboardItem();
        item.setCharValue("a");
        item.setShiftChar("A");
        item.setX(1);
        item.setY(2);
        item.setW(3);
        item.setColorClass("bg-primary");
        item.setTextClass("font-serif");
        item.setDescription("ash");
        item.setShiftDescription("capital ash");
        return item;
    }

    private static TeamWorkspace workspace(String id) {
        TeamWorkspace workspace = new TeamWorkspace("Target", null, "owner");
        workspace.setId(id);
        return workspace;
    }
}
