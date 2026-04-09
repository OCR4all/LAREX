package de.uniwue.zpd.dachs.larex.backend.controller.dataset;

import de.uniwue.zpd.dachs.larex.backend.service.dataset.DatasetService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/public/dataset-releases")
public class PublicDatasetReleaseController {

    private final DatasetService datasetService;

    public PublicDatasetReleaseController(DatasetService datasetService) {
        this.datasetService = datasetService;
    }

    @GetMapping("/{sharePublicId}/download")
    public ResponseEntity<Resource> downloadSharedRelease(
            @PathVariable String sharePublicId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) throws IOException {
        return buildResponse(sharePublicId, authorizationHeader, true);
    }

    @RequestMapping(path = "/{sharePublicId}/download", method = RequestMethod.HEAD)
    public ResponseEntity<Resource> headSharedRelease(
            @PathVariable String sharePublicId,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) throws IOException {
        return buildResponse(sharePublicId, authorizationHeader, false);
    }

    private ResponseEntity<Resource> buildResponse(
            String sharePublicId,
            String authorizationHeader,
            boolean trackUsage) throws IOException {
        DatasetService.SharedReleaseDownload download = datasetService.downloadSharedReleasePackage(sharePublicId, authorizationHeader, trackUsage);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(download.contentLength());
        headers.setContentDisposition(ContentDisposition.attachment().filename(download.fileName()).build());
        headers.setCacheControl("private, no-store, max-age=0");
        headers.set("X-Checksum-Sha256", download.checksumSha256());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(download.absolutePath()));
    }
}
