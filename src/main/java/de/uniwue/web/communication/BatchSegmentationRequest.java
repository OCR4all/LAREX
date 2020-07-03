package de.uniwue.web.communication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.uniwue.web.facade.segmentation.SegmentationSettings;
import de.uniwue.web.model.PageAnnotations;

import java.util.List;

/**
 * Communication object for the gui to request the segmentation of different
 * pages. Contains the segmentation settings and a list of page numbers to
 * segment.
 *
 */
public class BatchSegmentationRequest {

    @JsonProperty("settings")
    private SegmentationSettings settings;
    @JsonProperty("pages")
    private List<Integer> pages;
    @JsonProperty("save")
    private boolean save;
    @JsonProperty("bookid")
    private Integer bookid;
    @JsonProperty("version")
    private String version;

    @JsonCreator
    public BatchSegmentationRequest(@JsonProperty("settings") SegmentationSettings settings,
                                    @JsonProperty("pages") List<Integer> pages,
                                    @JsonProperty("save") boolean save,
                                    @JsonProperty("bookid") Integer bookid,
                                    @JsonProperty("version") String version) {
        this.pages = pages;
        this.settings = settings;
        this.save = save;
        this.bookid = bookid;
        this.version = version;
    }

    public List<Integer> getPages() {
        return pages;
    }

    public SegmentationSettings getSettings() {
        return settings;
    }

    public boolean getSave() { return save; }

    public Integer getBookid() {
        return bookid;
    }

    public String getVersion() {
        return version;
    }

}
