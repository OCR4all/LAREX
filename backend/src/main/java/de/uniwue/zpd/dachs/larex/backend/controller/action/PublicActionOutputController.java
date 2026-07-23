package de.uniwue.zpd.dachs.larex.backend.controller.action;

import de.uniwue.zpd.dachs.larex.backend.service.action.ActionOutputService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/public/action-outputs")
public class PublicActionOutputController {
    private final ActionOutputService outputService;

    public PublicActionOutputController(ActionOutputService outputService) {
        this.outputService = outputService;
    }

    @GetMapping("/{sharePublicId}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String sharePublicId,
                                                           @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        ActionOutputService.BundleDownload bundle = outputService.prepareSharedBundle(sharePublicId, authorization, true);
        return response(bundle, stream -> outputService.writeBundle(bundle.outputId(), stream));
    }

    @RequestMapping(path = "/{sharePublicId}/download", method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(@PathVariable String sharePublicId,
                                     @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        ActionOutputService.BundleDownload bundle = outputService.prepareSharedBundle(sharePublicId, authorization, false);
        HttpHeaders headers = headers(bundle.fileName());
        return ResponseEntity.ok().headers(headers).build();
    }

    private ResponseEntity<StreamingResponseBody> response(ActionOutputService.BundleDownload bundle,
                                                            StreamingResponseBody body) {
        return ResponseEntity.ok().headers(headers(bundle.fileName())).body(body);
    }

    private HttpHeaders headers(String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        headers.setCacheControl(CacheControl.noStore().mustRevalidate());
        return headers;
    }
}
