package de.uniwue.zpd.dachs.larex.backend.exception;

/** The page failure was durably acknowledged; the processor may continue. */
public class ActionPageResultRejectedException extends RuntimeException {
    private final String pageId;

    public ActionPageResultRejectedException(String pageId, String reason) {
        super(reason);
        this.pageId = pageId;
    }

    public String getPageId() { return pageId; }
}
