package de.uniwue.zpd.dachs.larex.backend.exception;

import de.uniwue.zpd.dachs.larex.backend.service.admin.ErrorEventContextResolver;
import de.uniwue.zpd.dachs.larex.backend.service.admin.ErrorEventService;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class DownloadExceptionHandlerTest {
    @Test
    void committedDownloadFailureDoesNotTryToAppendJsonToBinaryResponse() throws Exception {
        var mvc = standaloneSetup(new FailingDownloadController())
                .setControllerAdvice(new GlobalExceptionHandler(mock(ErrorEventService.class),
                        mock(ErrorEventContextResolver.class), new ObjectMapper()))
                .build();
        MvcResult pending = mvc.perform(get("/partial-download"))
                .andExpect(request().asyncStarted()).andReturn();

        mvc.perform(asyncDispatch(pending))
                .andExpect(content().string("partial"));
    }

    @Test
    void uncommittedDownloadFailureReturnsJsonInsteadOfASecondSerializationError() throws Exception {
        var mvc = standaloneSetup(new FailingDownloadController())
                .setControllerAdvice(new GlobalExceptionHandler(mock(ErrorEventService.class),
                        mock(ErrorEventContextResolver.class), new ObjectMapper()))
                .build();
        MvcResult pending = mvc.perform(get("/download"))
                .andExpect(request().asyncStarted()).andReturn();

        mvc.perform(asyncDispatch(pending))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred. Please try again later."));
    }

    @RestController
    static class FailingDownloadController {
        @GetMapping("/partial-download")
        ResponseEntity<StreamingResponseBody> partialDownload(HttpServletResponse response) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(output -> {
                        output.write("partial".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        output.flush();
                        response.flushBuffer();
                        throw new IOException("Could not finish download");
                    });
        }

        @GetMapping("/download")
        ResponseEntity<StreamingResponseBody> download() {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"download.zip\"")
                    .body(output -> { throw new IOException("Could not read download"); });
        }
    }
}
