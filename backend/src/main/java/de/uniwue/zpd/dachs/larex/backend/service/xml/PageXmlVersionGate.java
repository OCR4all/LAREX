package de.uniwue.zpd.dachs.larex.backend.service.xml;

import com.maxnth.page4j.pagexml.PageXmlReadResult;
import com.maxnth.page4j.pagexml.PageXmlVersion;

public final class PageXmlVersionGate {

    public static final PageXmlVersion NEWEST_READABLE = PageXmlVersion.V2024_07_15;

    private PageXmlVersionGate() {}

    public static PageXmlReadResult requireReadable(PageXmlReadResult read) {
        if (read.version().compareTo(NEWEST_READABLE) > 0) {
            throw new IllegalArgumentException("PAGE XML " + read.version().date()
                    + " is not supported yet; LAREX reads PAGE XML up to " + NEWEST_READABLE.date());
        }
        return read;
    }
}
