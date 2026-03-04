package de.uniwue.zpd.dachs.larex.backend.dto.page.region;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.geometry.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.layout.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.metadata.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.readingorder.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.style.*;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.*;

/**
 * Region kinds matching page4j's RegionType.
 * These correspond to PAGE XML element names.
 */
public enum RegionKind {
    TextRegion,
    ImageRegion,
    LineDrawingRegion,
    GraphicRegion,
    TableRegion,
    ChartRegion,
    MapRegion,
    SeparatorRegion,
    MathsRegion,
    ChemRegion,
    MusicRegion,
    AdvertRegion,
    NoiseRegion,
    UnknownRegion,
    CustomRegion;

    /**
     * Convert from page4j RegionType name.
     */
    public static RegionKind fromPage4jName(String name) {
        try {
            return RegionKind.valueOf(name);
        } catch (IllegalArgumentException e) {
            return UnknownRegion;
        }
    }

    /**
     * Get the PAGE XML element name.
     */
    public String toXmlElementName() {
        return this.name();
    }
}
