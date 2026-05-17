package de.uniwue.zpd.dachs.larex.backend.service.upload;

import de.uniwue.zpd.dachs.larex.backend.config.UploadProperties;
import de.uniwue.zpd.dachs.larex.backend.dto.UploadSessionDto;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession.UploadSessionStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSession;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile.UploadFileStatus;
import de.uniwue.zpd.dachs.larex.backend.entity.UploadSessionFile;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.upload.UploadSessionRepository;
import de.uniwue.zpd.dachs.larex.backend.service.upload.events.UploadFileReassembledEvent;
import de.uniwue.zpd.dachs.larex.backend.service.upload.events.UploadSessionFinalizedEvent;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import de.uniwue.zpd.dachs.larex.backend.util.ImageFileUtils;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;


import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ChunkedUploadService {

    private static final Logger log = LoggerFactory.getLogger(ChunkedUploadService.class);

    private final UploadSessionRepository sessionRepository;
    private final UploadSessionFileRepository fileRepository;
    private final ProjectRepository projectRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final UploadDirectoryPreflightService uploadDirectoryPreflightService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final UploadSessionEventBroadcaster uploadSessionEventBroadcaster;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;
    private final UploadProperties uploadProperties;
    private String tempDirectory;

    @PostConstruct
    private void initTempDirectory() {
        uploadDirectoryPreflightService.ensureDirectoriesReady();
        try {
            Path tempPath = Paths.get(tempDirectory).toAbsolutePath();
            Files.createDirectories(tempPath);
            this.tempDirectory = tempPath.toString();
            log.info("ChunkedUploadService: Temp directory initialized: {}", this.tempDirectory);
        } catch (IOException e) {
            log.warn("Could not initialize temp directory: {}. Chunked uploads may fail.", tempDirectory);
        }
    }

    public ChunkedUploadService(UploadSessionRepository sessionRepository,
                                UploadSessionFileRepository fileRepository,
                                ProjectRepository projectRepository,
                                WorkspaceAccessService workspaceAccessService,
                                UploadDirectoryPreflightService uploadDirectoryPreflightService,
                                ApplicationEventPublisher applicationEventPublisher,
                                UploadSessionEventBroadcaster uploadSessionEventBroadcaster,
                                WorkspaceQuotaGuardService workspaceQuotaGuardService,
                                UploadProperties uploadProperties) {
        this.sessionRepository = sessionRepository;
        this.fileRepository = fileRepository;
        this.projectRepository = projectRepository;
        this.workspaceAccessService = workspaceAccessService;
        this.uploadDirectoryPreflightService = uploadDirectoryPreflightService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.uploadSessionEventBroadcaster = uploadSessionEventBroadcaster;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
        this.uploadProperties = uploadProperties;
        this.tempDirectory = uploadProperties.getTempDirectory().toString();
    }

    public UploadSessionDto.SessionResponse createSession(String userId, String workspaceId, String projectId,
                                                          UploadSessionDto.CreateSessionRequest request) {
        workspaceAccessService.requireManageProjectsAccess(workspaceId, userId);

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + projectId));

        if (!project.getLibrary().getWorkspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("Project does not belong to workspace");
        }

        // Check concurrent session limit
        List<UploadSessionStatus> activeStatuses = List.of(
                UploadSessionStatus.PENDING,
                UploadSessionStatus.UPLOADING,
                UploadSessionStatus.PROCESSING
        );
        long activeSessionCount = sessionRepository.countActiveSessionsByUserId(userId, activeStatuses);
        if (activeSessionCount >= uploadProperties.getMaxConcurrentSessions()) {
            throw new IllegalArgumentException("Maximum concurrent upload sessions reached: " + uploadProperties.getMaxConcurrentSessions());
        }

        // Calculate total bytes and create session
        long totalBytes = request.files().stream()
                .mapToLong(UploadSessionDto.FileMetadata::fileSize)
                .sum();
        long reservedBytes = workspaceQuotaGuardService.reserveBytesOrThrow(
                workspaceId,
                totalBytes,
                "chunked-upload-session"
        );

        try {
            UploadSession session = new UploadSession(
                    projectId,
                    workspaceId,
                    userId,
                    request.files().size(),
                    totalBytes
            );
            session.setReservedBytes(reservedBytes);
            session.setQuotaReservationReleased(false);

            session = sessionRepository.save(session);

            // Create session file entries
            for (UploadSessionDto.FileMetadata fileMeta : request.files()) {
                int chunkCount = calculateChunkCount(fileMeta.fileSize());

                ImageFileUtils.ImageNameInfo nameInfo = ImageFileUtils.parseImageName(fileMeta.fileName());

                UploadSessionFile sessionFile = new UploadSessionFile(
                        fileMeta.fileName(),
                        fileMeta.fileSize(),
                        fileMeta.mimeType(),
                        nameInfo.baseName(),
                        nameInfo.variant(),
                        chunkCount
                );
                session.addFile(sessionFile);
                fileRepository.save(sessionFile);
            }

            // Create temp directory for this session
            Path sessionTempDir = Paths.get(tempDirectory, session.getId());
            Files.createDirectories(sessionTempDir);

            log.info("Created upload session {} for project {} with {} files",
                    session.getId(), projectId, request.files().size());

            return convertToSessionResponse(session);
        } catch (IOException e) {
            workspaceQuotaGuardService.releaseReservation(workspaceId, reservedBytes);
            throw new RuntimeException("Failed to initialize upload session", e);
        } catch (RuntimeException e) {
            workspaceQuotaGuardService.releaseReservation(workspaceId, reservedBytes);
            throw e;
        }
    }

    public UploadSessionDto.UploadChunkResponse uploadChunk(String userId, String sessionId, String fileId,
                                                            int chunkIndex, int totalChunks,
                                                            MultipartFile chunkData) throws IOException {
        UploadSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload session not found: " + sessionId));

        if (session.getStatus() == UploadSessionStatus.COMPLETED ||
            session.getStatus() == UploadSessionStatus.FAILED ||
            session.getStatus() == UploadSessionStatus.CANCELLED) {
            throw new IllegalArgumentException("Upload session is not active");
        }

        UploadSessionFile sessionFile = fileRepository.findByIdAndSessionId(fileId, sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("File not found in session: " + fileId));

        // Update session status if first chunk
        if (session.getStatus() == UploadSessionStatus.PENDING) {
            session.setStatus(UploadSessionStatus.UPLOADING);
            sessionRepository.save(session);
            uploadSessionEventBroadcaster.broadcastSessionState(session.getId(), "upload-started");
        }

        // Update file status
        if (sessionFile.getStatus() == UploadFileStatus.PENDING) {
            sessionFile.setStatus(UploadFileStatus.UPLOADING);
        }

        // Save chunk to temp file
        Path chunkPath = getChunkPath(sessionId, fileId, chunkIndex);
        Files.createDirectories(chunkPath.getParent());
        chunkData.transferTo(chunkPath.toFile());

        sessionFile.incrementChunksReceived();

        // Check if file is fully uploaded
        boolean fileComplete = sessionFile.isFullyUploaded();
        if (fileComplete) {
            sessionFile.setStatus(UploadFileStatus.UPLOADED);
            // Reassemble chunks into final temp file
            reassembleChunks(session, sessionFile);
        }

        fileRepository.save(sessionFile);
        if (fileComplete) {
            uploadSessionEventBroadcaster.broadcastFileState(sessionId, sessionFile);
            uploadSessionEventBroadcaster.broadcastSessionState(sessionId, "file-uploaded");
            applicationEventPublisher.publishEvent(new UploadFileReassembledEvent(sessionId, fileId));
        }

        log.debug("Received chunk {}/{} for file {} in session {}",
                chunkIndex + 1, totalChunks, fileId, sessionId);

        return new UploadSessionDto.UploadChunkResponse(
                fileId,
                sessionFile.getChunksReceived(),
                sessionFile.getChunkCount(),
                fileComplete
        );
    }

    public UploadSessionDto.SessionResponse getSession(String userId, String sessionId) {
        UploadSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload session not found: " + sessionId));

        return convertToSessionResponse(session);
    }

    public List<UploadSessionDto.SessionSummaryResponse> getUserSessions(String userId) {
        List<UploadSessionStatus> activeStatuses = List.of(
                UploadSessionStatus.PENDING,
                UploadSessionStatus.UPLOADING,
                UploadSessionStatus.PROCESSING
        );

        return sessionRepository.findByUserIdAndStatusIn(userId, activeStatuses).stream()
                .map(this::convertToSessionSummaryResponse)
                .toList();
    }

    public void finalizeSession(String userId, String sessionId,
                                UploadSessionDto.FinalizeSessionRequest request) {
        UploadSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload session not found: " + sessionId));

        if (session.getStatus() != UploadSessionStatus.UPLOADING) {
            throw new IllegalArgumentException("Session cannot be finalized in current state: " + session.getStatus());
        }

        // Check all files are uploaded
        List<UploadSessionFile> pendingFiles = fileRepository.findBySessionIdAndStatusIn(
                sessionId,
                List.of(UploadFileStatus.PENDING, UploadFileStatus.UPLOADING)
        );

        if (!pendingFiles.isEmpty()) {
            throw new IllegalArgumentException("Not all files have been uploaded. Pending: " + pendingFiles.size());
        }

        List<UploadSessionFile> uploadedFiles = fileRepository.findBySessionIdAndStatus(sessionId, UploadFileStatus.UPLOADED);
        for (UploadSessionFile uploadedFile : uploadedFiles) {
            if (uploadedFile.getTempFilePath() == null || uploadedFile.getTempFilePath().isBlank()) {
                continue;
            }
            Path tempFilePath = Paths.get(uploadedFile.getTempFilePath());
            if (!Files.exists(tempFilePath)) {
                continue;
            }

            long actualSize;
            try {
                actualSize = Files.size(tempFilePath);
            } catch (IOException e) {
                failSessionAndReleaseReservation(session, "Failed to validate uploaded file size: " + uploadedFile.getOriginalFileName(), true);
                throw new IllegalArgumentException("Failed to validate uploaded file size");
            }

            if (actualSize > uploadedFile.getFileSize()) {
                failSessionAndReleaseReservation(
                        session,
                        "Uploaded file exceeds declared size: " + uploadedFile.getOriginalFileName(),
                        true
                );
                throw new IllegalArgumentException("Uploaded file exceeds declared size");
            }
        }

        // Apply conflict resolutions
        if (request != null && request.conflictResolutions() != null) {
            Map<String, UploadSessionFile> filesById = fileRepository.findAllById(
                            request.conflictResolutions().stream()
                                    .map(UploadSessionDto.ConflictResolution::fileId)
                                    .distinct()
                                    .toList()
                    ).stream()
                    .collect(java.util.stream.Collectors.toMap(UploadSessionFile::getId, f -> f));
            List<UploadSessionFile> toSave = new ArrayList<>();

            for (UploadSessionDto.ConflictResolution resolution : request.conflictResolutions()) {
                UploadSessionFile file = filesById.get(resolution.fileId());
                if (file != null) {
                    file.setConflictResolution(resolution.resolution());
                    toSave.add(file);
                }
            }
            if (!toSave.isEmpty()) {
                fileRepository.saveAll(toSave);
            }
        }

        // Update status - async processing will be triggered by the controller
        // after this transaction commits to avoid race conditions
        session.setStatus(UploadSessionStatus.PROCESSING);
        sessionRepository.save(session);
        uploadSessionEventBroadcaster.broadcastSessionState(sessionId, "session-finalized");
        applicationEventPublisher.publishEvent(new UploadSessionFinalizedEvent(sessionId));

        log.info("Upload session {} finalized and ready for async processing", sessionId);
    }

    public void cancelSession(String userId, String sessionId) {
        UploadSession session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload session not found: " + sessionId));

        if (session.getStatus() == UploadSessionStatus.CANCELLED) {
            return;
        }

        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            throw new IllegalArgumentException("Cannot cancel a completed session");
        }

        List<UploadSessionFile> files = fileRepository.findBySessionId(sessionId);
        List<UploadSessionFile> unfinishedFiles = new ArrayList<>();
        for (UploadSessionFile file : files) {
            UploadFileStatus status = file.getStatus();
            if (status == UploadFileStatus.PENDING
                    || status == UploadFileStatus.UPLOADING
                    || status == UploadFileStatus.UPLOADED
                    || status == UploadFileStatus.PROCESSING) {
                file.setStatus(UploadFileStatus.SKIPPED);
                if (file.getErrorMessage() == null || file.getErrorMessage().isBlank()) {
                    file.setErrorMessage("Upload cancelled by user");
                }
                unfinishedFiles.add(file);
            }
        }
        if (!unfinishedFiles.isEmpty()) {
            fileRepository.saveAll(unfinishedFiles);
            for (UploadSessionFile unfinishedFile : unfinishedFiles) {
                uploadSessionEventBroadcaster.broadcastFileState(sessionId, unfinishedFile);
            }
        }

        session.setStatus(UploadSessionStatus.CANCELLED);
        session.setCompletedAt(LocalDateTime.now());
        releaseSessionReservationIfNeeded(session, true);
        sessionRepository.save(session);
        uploadSessionEventBroadcaster.broadcastSessionState(sessionId, "cancelled");

        // Clean up temp files
        cleanupSessionTempFiles(sessionId);

        log.info("Cancelled upload session: {}", sessionId);
    }

    private void reassembleChunks(UploadSession session, UploadSessionFile sessionFile) throws IOException {
        Path assembledFilePath = Paths.get(tempDirectory, session.getId(),
                sessionFile.getId() + "_" + sessionFile.getOriginalFileName());

        try (OutputStream out = Files.newOutputStream(assembledFilePath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            for (int i = 0; i < sessionFile.getChunkCount(); i++) {
                Path chunkPath = getChunkPath(session.getId(), sessionFile.getId(), i);
                if (Files.exists(chunkPath)) {
                    Files.copy(chunkPath, out);
                    Files.delete(chunkPath);
                }
            }
        }

        sessionFile.setTempFilePath(assembledFilePath.toString());
        log.debug("Reassembled file {} for session {}", sessionFile.getOriginalFileName(), session.getId());
    }

    private Path getChunkPath(String sessionId, String fileId, int chunkIndex) {
        return Paths.get(tempDirectory, sessionId, fileId, "chunk_" + chunkIndex);
    }

    private int calculateChunkCount(long fileSize) {
        if (fileSize <= 0) return 1;
        return (int) Math.ceil((double) fileSize / uploadProperties.getChunkSizeBytes());
    }

    public void cleanupSessionTempFiles(String sessionId) {
        try {
            Path sessionDir = Paths.get(tempDirectory, sessionId);
            if (Files.exists(sessionDir)) {
                Files.walk(sessionDir)
                        .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.warn("Failed to delete temp file: {}", path, e);
                            }
                        });
            }
        } catch (IOException e) {
            log.error("Failed to cleanup temp files for session: {}", sessionId, e);
        }
    }

    private void failSessionAndReleaseReservation(UploadSession session, String errorMessage, boolean cleanupTempFiles) {
        LocalDateTime completedAt = LocalDateTime.now();
        session.setStatus(UploadSessionStatus.FAILED);
        session.setErrorMessage(errorMessage);
        session.setCompletedAt(completedAt);
        releaseSessionReservationIfNeeded(session, true);
        sessionRepository.save(session);
        uploadSessionEventBroadcaster.broadcastSessionState(session.getId(), "failed");
        if (cleanupTempFiles) {
            cleanupSessionTempFiles(session.getId());
        }
    }

    private void releaseSessionReservationIfNeeded(UploadSession session, boolean syncUsage) {
        if (session == null || session.isQuotaReservationReleased() || session.getReservedBytes() <= 0) {
            return;
        }

        if (syncUsage) {
            workspaceQuotaGuardService.syncUsageAndReleaseReservation(session.getWorkspaceId(), session.getReservedBytes());
        } else {
            workspaceQuotaGuardService.releaseReservation(session.getWorkspaceId(), session.getReservedBytes());
        }

        session.setQuotaReservationReleased(true);
        session.setReservedBytes(0L);
    }

    private UploadSessionDto.SessionResponse convertToSessionResponse(UploadSession session) {
        List<UploadSessionDto.FileResponse> fileResponses = session.getFiles().stream()
                .map(this::convertToFileResponse)
                .toList();

        return new UploadSessionDto.SessionResponse(
                session.getId(),
                session.getProjectId(),
                session.getWorkspaceId(),
                session.getStatus(),
                session.getTotalFiles(),
                session.getProcessedFiles(),
                session.getFailedFiles(),
                session.getTotalBytes(),
                session.getProcessedBytes(),
                session.getProgressPercent(),
                fileResponses,
                session.getErrorMessage(),
                session.getCreated(),
                session.getUpdated(),
                session.getCompletedAt()
        );
    }

    private UploadSessionDto.SessionSummaryResponse convertToSessionSummaryResponse(UploadSession session) {
        return new UploadSessionDto.SessionSummaryResponse(
                session.getId(),
                session.getProjectId(),
                session.getStatus(),
                session.getTotalFiles(),
                session.getProcessedFiles(),
                session.getFailedFiles(),
                session.getProgressPercent(),
                session.getCreated(),
                session.getUpdated(),
                session.getCompletedAt()
        );
    }

    private UploadSessionDto.FileResponse convertToFileResponse(UploadSessionFile file) {
        return new UploadSessionDto.FileResponse(
                file.getId(),
                file.getOriginalFileName(),
                file.getFileSize(),
                file.getMimeType(),
                file.getBaseName(),
                file.getVariant(),
                file.getStatus(),
                file.getChunkCount(),
                file.getChunksReceived(),
                file.getErrorMessage(),
                file.getCreatedPageId(),
                file.getCreatedPageImageId(),
                file.getConflictType(),
                file.getConflictResolution()
        );
    }
}
