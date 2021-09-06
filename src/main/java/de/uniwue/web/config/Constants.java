package de.uniwue.web.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class Constants {
    public static final List<String> IMG_EXTENSIONS = Arrays.asList("png", "jpg", "jpeg", "tif", "tiff");
    // TODO: Hacky solution, should be removed when all usages are streamlined
    public static final List<String> IMG_EXTENSIONS_DOTTED = IMG_EXTENSIONS.stream().map(e -> "." + e).collect(Collectors.toList());
}
