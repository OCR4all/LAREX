package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.action.ActionOutputDto;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionOutput;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionOutputFile;
import de.uniwue.zpd.dachs.larex.backend.entity.ActionRun;
import de.uniwue.zpd.dachs.larex.backend.entity.Project;
import de.uniwue.zpd.dachs.larex.backend.entity.StoredFile;
import de.uniwue.zpd.dachs.larex.backend.exception.ResourceNotFoundException;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionOutputFileRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.action.ActionOutputRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.project.ProjectRepository;
import de.uniwue.zpd.dachs.larex.backend.repository.storage.StoredFileRepository;
import de.uniwue.zpd.dachs.larex.backend.service.storage.HierarchicalFileStorageService;
import de.uniwue.zpd.dachs.larex.backend.service.storage.WorkspaceQuotaGuardService;
import de.uniwue.zpd.dachs.larex.backend.service.upload.UploadPathService;
import de.uniwue.zpd.dachs.larex.backend.service.workspace.WorkspaceAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ActionOutputService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DateTimeFormatter BUNDLE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ActionOutputRepository outputRepository;
    private final ActionOutputFileRepository outputFileRepository;
    private final ProjectRepository projectRepository;
    private final StoredFileRepository storedFileRepository;
    private final HierarchicalFileStorageService fileStorageService;
    private final UploadPathService uploadPathService;
    private final WorkspaceAccessService workspaceAccessService;
    private final WorkspaceQuotaGuardService workspaceQuotaGuardService;

    @Value("${larex.action-outputs.share-public-base-url:${cors.allowed-origin}/api/public/action-outputs}")
    private String sharePublicBaseUrl;

    public ActionOutputService(ActionOutputRepository outputRepository,
                               ActionOutputFileRepository outputFileRepository,
                               ProjectRepository projectRepository,
                               StoredFileRepository storedFileRepository,
                               HierarchicalFileStorageService fileStorageService,
                               UploadPathService uploadPathService,
                               WorkspaceAccessService workspaceAccessService,
                               WorkspaceQuotaGuardService workspaceQuotaGuardService) {
        this.outputRepository = outputRepository;
        this.outputFileRepository = outputFileRepository;
        this.projectRepository = projectRepository;
        this.storedFileRepository = storedFileRepository;
        this.fileStorageService = fileStorageService;
        this.uploadPathService = uploadPathService;
        this.workspaceAccessService = workspaceAccessService;
        this.workspaceQuotaGuardService = workspaceQuotaGuardService;
    }

    @Transactional
    public void createDraft(ActionRun run, Project project) {
        if (!run.getProcessorDefinition().isOutputsFiles()
                || outputRepository.findBySourceRunId(run.getId()).isPresent()) {
            return;
        }
        ActionOutput output = new ActionOutput();
        output.setProject(project);
        output.setWorkspaceId(run.getWorkspaceId());
        output.setSourceRunId(run.getId());
        output.setProcessorDefinitionId(run.getProcessorDefinition().getId());
        output.setProcessorKey(run.getProcessorDefinition().getProcessorKey());
        output.setProcessorName(run.getProcessorDefinition().getName());
        output.setCreatedByUserId(run.getCreatedByUserId());
        output.setRetentionDays(project.getOutputRetentionDays());
        outputRepository.save(output);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> storeResultFile(ActionRun run,
                                                ActionDto.ResultFile resultFile,
                                                MultipartFile file) throws IOException {
        ActionOutput output = outputRepository.findBySourceRunId(run.getId())
                .orElseThrow(() -> new IllegalStateException("Action output draft not found"));
        if (output.getStatus() != ActionOutput.Status.DRAFT) {
            throw new IllegalStateException("Action output is no longer writable");
        }
        String fileName = fileStorageService.sanitizeOriginalFilename(
                firstNonBlank(resultFile.fileName(), file.getOriginalFilename(), "output.bin")
        );
        HierarchicalFileStorageService.StoredFileDescriptor descriptor = fileStorageService.storeMultipartFile(
                new NamedMultipartFile(file, fileName),
                run.getWorkspaceId(),
                run.getProjectId(),
                StoredFile.StoredFileType.OUTPUT,
                run.getCreatedByUserId()
        );
        StoredFile storedFile = storedFileRepository.findById(descriptor.uuid())
                .orElseThrow(() -> new IllegalStateException("Stored output file metadata not found"));

        ActionOutputFile outputFile = new ActionOutputFile();
        outputFile.setOutput(output);
        outputFile.setStoredFile(storedFile);
        outputFile.setPageId(blankToNull(resultFile.pageId()));
        outputFile.setFileName(descriptor.originalFilename());
        outputFile.setMimeType(descriptor.mimeType());
        outputFile.setSizeBytes(descriptor.sizeBytes());
        outputFile.setChecksumSha256(descriptor.checksumSha256());
        outputFileRepository.save(outputFile);

        output.setFileCount(output.getFileCount() + 1);
        output.setTotalSizeBytes(output.getTotalSizeBytes() + descriptor.sizeBytes());
        outputRepository.save(output);

        Map<String, Object> stored = new HashMap<>();
        stored.put("type", "file");
        stored.put("outputId", output.getId());
        stored.put("outputFileId", outputFile.getId());
        stored.put("fileName", descriptor.originalFilename());
        if (resultFile.pageId() != null) stored.put("pageId", resultFile.pageId());
        return stored;
    }

    @Transactional
    public void finalizeDraft(String runId, LocalDateTime completedAt) {
        Optional<ActionOutput> outputOpt = outputRepository.findBySourceRunId(runId);
        if (outputOpt.isEmpty()) return;
        ActionOutput output = outputOpt.get();
        if (output.getFileCount() == 0) {
            outputRepository.delete(output);
            return;
        }
        output.setStatus(ActionOutput.Status.READY);
        output.setCompletedAt(completedAt);
        if (output.getRetentionDays() != null) {
            output.setExpiresAt(completedAt.plusDays(output.getRetentionDays()));
        }
        outputRepository.save(output);
    }

    @Transactional
    public void discardDraft(String runId) {
        outputRepository.findBySourceRunId(runId)
                .filter(output -> output.getStatus() == ActionOutput.Status.DRAFT)
                .ifPresent(this::deleteOutputFilesAndRecord);
    }

    @Transactional(readOnly = true)
    public List<ActionOutputDto.OutputResponse> listOutputs(String workspaceId, String projectId, String userId) {
        requireProject(workspaceId, projectId);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        return outputRepository.findByProjectIdAndStatusOrderByCompletedAtDesc(projectId, ActionOutput.Status.READY)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FileDownload prepareFileDownload(String workspaceId, String projectId, String outputId,
                                            String fileId, String userId) throws IOException {
        requireProject(workspaceId, projectId);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        ActionOutput output = requireReadyOutput(projectId, outputId);
        ActionOutputFile file = output.getFiles().stream().filter(candidate -> candidate.getId().equals(fileId))
                .findFirst().orElseThrow(() -> new ResourceNotFoundException("Action output file", fileId));
        Path path = resolveExisting(file.getStoredFile().getStoragePath());
        return new FileDownload(file.getFileName(), file.getMimeType(), file.getSizeBytes(), file.getChecksumSha256(), path);
    }

    @Transactional(readOnly = true)
    public BundleDownload prepareBundleDownload(String workspaceId, String projectId, String outputId,
                                                String userId) {
        requireProject(workspaceId, projectId);
        workspaceAccessService.requireWorkspaceAccess(workspaceId, userId);
        ActionOutput output = requireReadyOutput(projectId, outputId);
        return new BundleDownload(output.getId(), bundleFileName(output));
    }

    @Transactional
    public void deleteOutput(String workspaceId, String projectId, String outputId, String userId) {
        requireProject(workspaceId, projectId);
        workspaceAccessService.requireManageProjectReleasesAndSharesAccess(workspaceId, userId);
        deleteOutputFilesAndRecord(requireReadyOutput(projectId, outputId));
        workspaceQuotaGuardService.syncUsage(workspaceId);
    }

    @Transactional
    public ActionOutputDto.ShareResponse createOrRotateShare(String workspaceId, String projectId, String outputId,
                                                             ActionOutputDto.ShareRequest request, String userId) {
        requireProject(workspaceId, projectId);
        workspaceAccessService.requireManageProjectReleasesAndSharesAccess(workspaceId, userId);
        ActionOutput output = requireReadyOutput(projectId, outputId);
        validateShareExpiry(output, request.expiresAt());
        LocalDateTime now = LocalDateTime.now();
        String publicId = generateOpaqueToken(18);
        String secret = generateOpaqueToken(32);
        output.setSharePublicId(publicId);
        output.setShareSecretHash(sha256(secret));
        output.setShareSecretPrefix(secret.substring(0, Math.min(8, secret.length())));
        output.setShareCreatedByUserId(userId);
        output.setShareCreatedAt(now);
        output.setShareExpiresAt(request.expiresAt());
        output.setShareRevokedAt(null);
        output.setShareLastUsedAt(null);
        output.setShareDownloadCount(0);
        outputRepository.save(output);
        return new ActionOutputDto.ShareResponse(buildShareUrl(publicId), secret, request.expiresAt(), now);
    }

    @Transactional
    public ActionOutputDto.OutputResponse updateShare(String workspaceId, String projectId, String outputId,
                                                      ActionOutputDto.ShareRequest request, String userId) {
        requireProject(workspaceId, projectId);
        workspaceAccessService.requireManageProjectReleasesAndSharesAccess(workspaceId, userId);
        ActionOutput output = requireReadyOutput(projectId, outputId);
        requireActiveShare(output);
        validateShareExpiry(output, request.expiresAt());
        output.setShareExpiresAt(request.expiresAt());
        return toResponse(outputRepository.save(output));
    }

    @Transactional
    public ActionOutputDto.OutputResponse revokeShare(String workspaceId, String projectId, String outputId,
                                                      String userId) {
        requireProject(workspaceId, projectId);
        workspaceAccessService.requireManageProjectReleasesAndSharesAccess(workspaceId, userId);
        ActionOutput output = requireReadyOutput(projectId, outputId);
        requireActiveShare(output);
        output.setShareRevokedAt(LocalDateTime.now());
        return toResponse(outputRepository.save(output));
    }

    @Transactional
    public BundleDownload prepareSharedBundle(String sharePublicId, String authorizationHeader, boolean trackUsage) {
        ActionOutput output = outputRepository.findBySharePublicId(sharePublicId)
                .orElseThrow(() -> new ResourceNotFoundException("Action output", sharePublicId));
        requireActiveShare(output);
        if (!matchesSecret(output.getShareSecretHash(), extractBearer(authorizationHeader))) {
            throw new ResourceNotFoundException("Action output", sharePublicId);
        }
        if (trackUsage) {
            output.setShareLastUsedAt(LocalDateTime.now());
            output.setShareDownloadCount(output.getShareDownloadCount() + 1);
            outputRepository.save(output);
        }
        return new BundleDownload(output.getId(), bundleFileName(output));
    }

    @Transactional(readOnly = true)
    public void writeBundle(String outputId, OutputStream destination) throws IOException {
        ActionOutput output = outputRepository.findById(outputId)
                .filter(candidate -> candidate.getStatus() == ActionOutput.Status.READY)
                .orElseThrow(() -> new ResourceNotFoundException("Action output", outputId));
        Map<String, Integer> names = new HashMap<>();
        try (ZipOutputStream zip = new ZipOutputStream(destination)) {
            for (ActionOutputFile file : output.getFiles()) {
                String entryName = uniqueZipName(file.getFileName(), names);
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream input = Files.newInputStream(resolveExisting(file.getStoredFile().getStoragePath()))) {
                    input.transferTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    @Scheduled(fixedDelayString = "${larex.actions.output-cleanup-interval-ms:3600000}")
    @Transactional
    public void deleteExpiredOutputs() {
        List<ActionOutput> candidates = new ArrayList<>(
                outputRepository.findByStatusAndExpiresAtBefore(ActionOutput.Status.READY, LocalDateTime.now()));
        candidates.addAll(outputRepository.findByStatus(ActionOutput.Status.DELETING));
        for (ActionOutput output : candidates) {
            deleteOutputFilesAndRecord(output);
            workspaceQuotaGuardService.syncUsage(output.getWorkspaceId());
        }
    }

    private boolean deleteOutputFilesAndRecord(ActionOutput output) {
        output.setStatus(ActionOutput.Status.DELETING);
        output.setShareRevokedAt(LocalDateTime.now());
        outputRepository.saveAndFlush(output);
        List<String> paths = output.getFiles().stream()
                .map(file -> file.getStoredFile().getStoragePath()).toList();
        fileStorageService.deleteStoredFiles(paths);
        boolean incomplete = paths.stream().anyMatch(path -> {
            try {
                return Files.exists(uploadPathService.resolve(path));
            } catch (RuntimeException ignored) {
                return true;
            }
        });
        if (incomplete) {
            return false;
        }
        outputRepository.delete(output);
        return true;
    }

    private ActionOutput requireReadyOutput(String projectId, String outputId) {
        return outputRepository.findByIdAndProjectId(outputId, projectId)
                .filter(output -> output.getStatus() == ActionOutput.Status.READY)
                .orElseThrow(() -> new ResourceNotFoundException("Action output", outputId));
    }

    private Project requireProject(String workspaceId, String projectId) {
        return projectRepository.findByIdAndLibraryWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    private ActionOutputDto.OutputResponse toResponse(ActionOutput output) {
        return new ActionOutputDto.OutputResponse(
                output.getId(), output.getSourceRunId(), output.getProcessorDefinitionId(),
                output.getProcessorKey(), output.getProcessorName(), output.getCreatedByUserId(),
                output.getFileCount(), output.getTotalSizeBytes(), output.getRetentionDays(),
                output.getExpiresAt(), output.getCompletedAt(), isShareActive(output),
                output.getShareSecretPrefix(), output.getShareCreatedAt(), output.getShareExpiresAt(),
                output.getShareRevokedAt(), output.getShareLastUsedAt(), output.getShareDownloadCount(),
                output.getFiles().stream().map(file -> new ActionOutputDto.FileResponse(
                        file.getId(), file.getPageId(), file.getFileName(), file.getMimeType(),
                        file.getSizeBytes(), file.getChecksumSha256(), file.getCreated()
                )).toList(), output.getCreated(), output.getUpdated()
        );
    }

    private Path resolveExisting(String storagePath) throws IOException {
        Path path = uploadPathService.resolve(storagePath);
        if (!Files.isRegularFile(path)) throw new IOException("Output file is missing");
        return path;
    }

    private void validateShareExpiry(ActionOutput output, LocalDateTime shareExpiry) {
        if (output.getExpiresAt() != null && shareExpiry.isAfter(output.getExpiresAt())) {
            throw new IllegalArgumentException("Share expiry cannot be later than output deletion");
        }
    }

    private boolean isShareActive(ActionOutput output) {
        return output.getSharePublicId() != null && output.getShareSecretHash() != null
                && output.getShareRevokedAt() == null && output.getShareExpiresAt() != null
                && output.getShareExpiresAt().isAfter(LocalDateTime.now());
    }

    private void requireActiveShare(ActionOutput output) {
        if (!isShareActive(output)) throw new ResourceNotFoundException("Action output share", output.getId());
    }

    private String bundleFileName(ActionOutput output) {
        LocalDateTime timestamp = output.getCompletedAt() == null ? LocalDateTime.now() : output.getCompletedAt();
        return safeName(output.getProcessorKey()) + "-" + BUNDLE_TIMESTAMP.format(timestamp) + ".zip";
    }

    private String uniqueZipName(String rawName, Map<String, Integer> names) {
        String safe = safeName(rawName);
        int count = names.merge(safe.toLowerCase(Locale.ROOT), 1, Integer::sum);
        if (count == 1) return safe;
        int dot = safe.lastIndexOf('.');
        return dot > 0 ? safe.substring(0, dot) + "-" + count + safe.substring(dot) : safe + "-" + count;
    }

    private String safeName(String value) {
        String safe = fileStorageService.sanitizeOriginalFilename(value);
        return safe.isBlank() ? "output" : safe;
    }

    private String buildShareUrl(String publicId) {
        return sharePublicBaseUrl.replaceAll("/+$", "") + "/" + publicId + "/download";
    }

    private String generateOpaqueToken(int bytes) {
        byte[] value = new byte[bytes];
        SECURE_RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private boolean matchesSecret(String expectedHash, String secret) {
        return expectedHash != null && secret != null
                && MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
                sha256(secret).getBytes(StandardCharsets.US_ASCII));
    }

    private String extractBearer(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        return blankToNull(header.substring(7));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "output.bin";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record FileDownload(String fileName, String mimeType, long contentLength,
                               String checksumSha256, Path absolutePath) {}
    public record BundleDownload(String outputId, String fileName) {}

    private static final class NamedMultipartFile implements MultipartFile {
        private final MultipartFile delegate;
        private final String name;
        private NamedMultipartFile(MultipartFile delegate, String name) { this.delegate = delegate; this.name = name; }
        public String getName() { return delegate.getName(); }
        public String getOriginalFilename() { return name; }
        public String getContentType() { return delegate.getContentType(); }
        public boolean isEmpty() { return delegate.isEmpty(); }
        public long getSize() { return delegate.getSize(); }
        public byte[] getBytes() throws IOException { return delegate.getBytes(); }
        public InputStream getInputStream() throws IOException { return delegate.getInputStream(); }
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException { delegate.transferTo(dest); }
    }
}
