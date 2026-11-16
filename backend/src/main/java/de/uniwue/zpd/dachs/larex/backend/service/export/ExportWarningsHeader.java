package de.uniwue.zpd.dachs.larex.backend.service.export;

import org.springframework.http.HttpHeaders;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ExportWarningsHeader {

    public static final String NAME = "X-Export-Warnings";
    static final int MAX_LINES = 10;

    private ExportWarningsHeader() {}

    public static void add(HttpHeaders headers, List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return;
        }
        headers.set(NAME, encode(warnings));
    }

    static String encode(List<String> warnings) {
        List<String> lines = new ArrayList<>(warnings.subList(0, Math.min(MAX_LINES, warnings.size())));
        if (warnings.size() > MAX_LINES) {
            int more = warnings.size() - MAX_LINES;
            lines.add("and " + more + " more " + (more == 1 ? "change" : "changes"));
        }
        return URLEncoder.encode(String.join("\n", lines), StandardCharsets.UTF_8).replace("+", "%20");
    }
}
