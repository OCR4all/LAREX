package de.uniwue.web.communication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.uniwue.web.model.PageAnnotations;

import java.util.Arrays;
import java.util.List;

/**
 * Communication object for the gui to request the export of different
 * pages. Contains a list of page numbers to
 * export and page annotations for each page.
 *
 */
public class BatchExportRequest {
    @JsonProperty("bookid")
    private Integer bookid;
    @JsonProperty("pages")
    private List<Integer> pages;
    @JsonProperty("segmentation")
    private List<PageAnnotations> segmentations;
    @JsonProperty("version")
    private String version;
    @JsonProperty("downloadPage")
    private Boolean downloadPages;

    @JsonCreator
    public BatchExportRequest(@JsonProperty("bookid") Integer bookid,
                              @JsonProperty("pages") List<Integer> pages,
                              @JsonProperty("segmentations") PageAnnotations[] segmentations,
                              @JsonProperty("version") String version) {
        this.bookid = bookid;
        this.pages = pages;
        this.segmentations = Arrays.asList(segmentations.clone());
        this.version = version;
    }
    public Integer getBookid() {
        return bookid;
    }

    public List<Integer> getPages() {
        return pages;
    }

    public List<PageAnnotations> getSegmentation() {
        return segmentations;
    }

    public String getVersion() {
        return version;
    }

    public Boolean getDownload() { return downloadPages; }
}
