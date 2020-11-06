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
public class BatchLoadRequest {
    @JsonProperty("bookid")
    private Integer bookid;
    @JsonProperty("pages")
    private List<Integer> pages;

    @JsonCreator
    public BatchLoadRequest(@JsonProperty("bookid") Integer bookid,
                              @JsonProperty("pages") List<Integer> pages) {
        this.bookid = bookid;
        this.pages = pages;
    }
    public Integer getBookid() {
        return bookid;
    }

    public List<Integer> getPages() {
        return pages;
    }

}
