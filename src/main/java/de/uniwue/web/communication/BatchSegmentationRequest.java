package de.uniwue.web.communication;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.uniwue.web.facade.segmentation.SegmentationSettings;

import java.util.Map;

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
    private Map<Integer, Double> pages;
    @JsonProperty("bookid")
    private Integer bookid;
    @JsonProperty("version")
    private String version;

    @JsonCreator
    public BatchSegmentationRequest(@JsonProperty("settings") SegmentationSettings settings,
                                    @JsonProperty("pages") Map<Integer, Double> pages,
                                    @JsonProperty("bookid") Integer bookid,
                                    @JsonProperty("version") String version) {
        this.pages = pages;
        this.settings = settings;
        this.bookid = bookid;
        this.version = version;
    }

    public Map<Integer, Double> getPages() {
        return pages;
    }

    public SegmentationSettings getSettings() {
        return settings;
    }

    public Integer getBookid() {
        return bookid;
    }

    public String getVersion() {
        return version;
    }

}
