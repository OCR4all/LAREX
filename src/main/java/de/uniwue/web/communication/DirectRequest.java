package de.uniwue.web.communication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import de.uniwue.web.model.PageAnnotations;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Communication object for the library to request the editor
 *
 */
public class DirectRequest {
    private String imagemapString;
    private String customFlag;
    private String customFolder;

    @JsonCreator
    public DirectRequest(String imagemapString,
                         String customFlag,
                         String customFolder) {
        this.imagemapString = imagemapString;
        this.customFlag = customFlag;
        if(customFolder != null) {
            this.customFolder = customFolder;
        } else {
            this.customFolder = "";
        }
    }

    public String getImagemapString() {
        return imagemapString;
    }

    public String getCustomFlag() {
        return customFlag;
    }

    public String getCustomFolder() {
        return customFolder;
    }

    public void setImagemapString(String imagemapString) {
        this.imagemapString = imagemapString;
    }

    public void setCustomFlag(String customFlag) {
        this.customFlag = customFlag;
    }

    public void setCustomFolder(String customFolder) {
        this.customFolder = customFolder;
    }
}
