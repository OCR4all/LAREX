package de.uniwue.web.communication;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Communication object for the library to request the editor
 *
 */
public class DirectRequest {
    private String fileMapString;
    private String mimeMapString;
    private String metsPath;
    private String customFlag;
    private String customFolder;

    @JsonCreator
    public DirectRequest(String fileMapString,
                         String mimeMapString,
                         String metsPath,
                         String customFlag,
                         String customFolder) {
        this.fileMapString = fileMapString;
        this.mimeMapString = mimeMapString;
        this.metsPath = metsPath;
        this.customFlag = customFlag;
        if(customFolder != null) {
            this.customFolder = customFolder;
        } else {
            this.customFolder = "";
        }
    }

    public String getFileMapString() {
        return fileMapString;
    }

    public String getCustomFlag() {
        return customFlag;
    }

    public String getCustomFolder() {
        return customFolder;
    }

    public void setFileMapString(String fileMapString) {
        this.fileMapString = fileMapString;
    }

    public void setCustomFlag(String customFlag) {
        this.customFlag = customFlag;
    }

    public void setCustomFolder(String customFolder) {
        this.customFolder = customFolder;
    }

    public String getMimeMapString() {
        return mimeMapString;
    }

    public String getMetsPath() {
        return metsPath;
    }
}
