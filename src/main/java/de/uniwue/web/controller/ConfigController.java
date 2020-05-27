package de.uniwue.web.controller;

import java.io.File;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import de.uniwue.web.config.LarexConfiguration;
import de.uniwue.web.io.FilePathManager;

@Controller
@Scope("request")
public class ConfigController {
    @Autowired
    private FilePathManager fileManager;
    @Autowired
    private LarexConfiguration config;

    /**
     * Returns whether LAREX is configured to be used in conjunction with OCR4all or not.
     */
    @RequestMapping(value = "config/ocr4all", method = RequestMethod.POST, headers = "Accept=*/*",
            produces = "application/json")
    public @ResponseBody Boolean isOCR4allMode() {
        config.read(new File(fileManager.getConfigurationFile()));
        String ocr4allMode = config.getSetting("ocr4all");
        return ocr4allMode.equals("enable");
    }
}
