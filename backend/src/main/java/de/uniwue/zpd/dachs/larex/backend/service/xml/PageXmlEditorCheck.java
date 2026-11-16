package de.uniwue.zpd.dachs.larex.backend.service.xml;

import com.maxnth.page4j.pagexml.PageReadException;
import de.uniwue.zpd.dachs.larex.backend.dto.PageXmlTextDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.io.parser.PageXmlToAnnotationParser;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PageXmlEditorCheck {

    static final String READ_ERROR = "PAGE_READ_ERROR";
    static final String EDITOR_ERROR = "EDITOR_CANNOT_OPEN";
    private static final Pattern POSITION = Pattern.compile("^(\\d+)(?::(\\d+))?: (.*)$", Pattern.DOTALL);

    private final PageXmlValidationService pageXmlValidationService;
    private final PageXmlToAnnotationParser parser;

    public PageXmlEditorCheck(PageXmlValidationService pageXmlValidationService, PageXmlToAnnotationParser parser) {
        this.pageXmlValidationService = pageXmlValidationService;
        this.parser = parser;
    }

    public PageXmlTextDto.XmlValidationResult validate(String xmlText) {
        String normalized = xmlText == null ? "" : xmlText;
        PageXmlTextDto.XmlValidationResult validation = pageXmlValidationService.validatePageXml(normalized);
        if (!validation.valid()) {
            return validation;
        }
        List<PageXmlTextDto.XmlValidationError> errors = check(normalized.getBytes(StandardCharsets.UTF_8));
        return errors.isEmpty()
                ? validation
                : new PageXmlTextDto.XmlValidationResult(false, errors, validation.pageVersion(), validation.namespace());
    }

    List<PageXmlTextDto.XmlValidationError> check(byte[] xml) {
        try {
            parser.parse(xml);
            return List.of();
        } catch (IOException | RuntimeException e) {
            PageReadException read = readException(e);
            return read != null ? readErrors(read) : List.of(error(1, 1, EDITOR_ERROR, rootMessage(e)));
        }
    }

    private static List<PageXmlTextDto.XmlValidationError> readErrors(PageReadException read) {
        List<PageXmlTextDto.XmlValidationError> errors = new ArrayList<>();
        for (String message : read.errors()) {
            Matcher position = POSITION.matcher(message);
            if (position.matches()) {
                int line = Integer.parseInt(position.group(1));
                int column = position.group(2) == null ? 1 : Integer.parseInt(position.group(2));
                errors.add(error(line, column, READ_ERROR, position.group(3)));
            } else {
                errors.add(error(read.line(), read.column(), READ_ERROR, message));
            }
        }
        if (errors.isEmpty()) {
            errors.add(error(read.line(), read.column(), READ_ERROR, read.getMessage()));
        }
        return errors;
    }

    private static PageReadException readException(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof PageReadException read) {
                return read;
            }
        }
        return null;
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? "The editor cannot open this PAGE XML" : message;
    }

    private static PageXmlTextDto.XmlValidationError error(int line, int column, String code, String message) {
        return new PageXmlTextDto.XmlValidationError(Math.max(1, line), Math.max(1, column), "error", code, message);
    }
}
