package de.uniwue.zpd.dachs.larex.backend.service.action;

import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.region.RegionDto;
import de.uniwue.zpd.dachs.larex.backend.dto.page.text.TextLineDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ActionResultPageMergeService {

    public PageDto replaceTargetRegions(PageDto existing, PageDto incoming, Set<String> selectedRegionIds) {
        Map<String, RegionDto> incomingRegions = new HashMap<>();
        collectRegions(incoming.regions(), incomingRegions);
        List<String> missing = selectedRegionIds.stream()
                .filter(id -> !incomingRegions.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Returned PAGE XML does not contain selected region(s): " + String.join(", ", missing));
        }
        List<RegionDto> regions = replaceRegions(existing.regions(), selectedRegionIds, incomingRegions);
        return copyPageDto(existing, regions);
    }

    public PageDto replaceTargetTextLines(PageDto existing, PageDto incoming, Set<String> selectedTextLineIds) {
        Map<String, TextLineDto> incomingTextLines = new HashMap<>();
        collectTextLines(incoming.regions(), incomingTextLines);
        List<String> missing = selectedTextLineIds.stream()
                .filter(id -> !incomingTextLines.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Returned PAGE XML does not contain selected textline(s): " + String.join(", ", missing));
        }
        return copyPageDto(existing, replaceTextLinesInRegions(existing.regions(), selectedTextLineIds, incomingTextLines));
    }

    public void collectTargetIds(List<RegionDto> regions, Set<String> regionIds, Set<String> textLineIds) {
        if (regions == null) {
            return;
        }
        for (RegionDto region : regions) {
            if (region.id() != null) {
                regionIds.add(region.id());
            }
            if (region.textLines() != null) {
                for (TextLineDto line : region.textLines()) {
                    if (line.id() != null) {
                        textLineIds.add(line.id());
                    }
                }
            }
            collectTargetIds(region.nestedRegions(), regionIds, textLineIds);
        }
    }

    private List<RegionDto> replaceRegions(List<RegionDto> regions, Set<String> selectedRegionIds, Map<String, RegionDto> incomingRegions) {
        if (regions == null) {
            return null;
        }
        List<RegionDto> next = new ArrayList<>();
        for (RegionDto region : regions) {
            RegionDto incoming = region.id() == null ? null : incomingRegions.get(region.id());
            if (incoming != null && selectedRegionIds.contains(region.id())) {
                next.add(copyRegionDto(region, incoming.textLines(), incoming.nestedRegions()));
            } else {
                next.add(copyRegionDto(region, region.textLines(), replaceRegions(region.nestedRegions(), selectedRegionIds, incomingRegions)));
            }
        }
        return next;
    }

    private List<RegionDto> replaceTextLinesInRegions(List<RegionDto> regions,
                                                      Set<String> selectedTextLineIds,
                                                      Map<String, TextLineDto> incomingTextLines) {
        if (regions == null) {
            return null;
        }
        List<RegionDto> next = new ArrayList<>();
        for (RegionDto region : regions) {
            List<TextLineDto> textLines = region.textLines();
            List<TextLineDto> nextTextLines = textLines;
            if (textLines != null) {
                nextTextLines = new ArrayList<>();
                for (TextLineDto line : textLines) {
                    TextLineDto incoming = line.id() == null ? null : incomingTextLines.get(line.id());
                    nextTextLines.add(incoming != null && selectedTextLineIds.contains(line.id()) ? incoming : line);
                }
            }
            List<RegionDto> nextNested = replaceTextLinesInRegions(region.nestedRegions(), selectedTextLineIds, incomingTextLines);
            next.add(copyRegionDto(region, nextTextLines, nextNested));
        }
        return next;
    }

    private void collectTextLines(List<RegionDto> regions, Map<String, TextLineDto> byId) {
        if (regions == null) {
            return;
        }
        for (RegionDto region : regions) {
            if (region.textLines() != null) {
                for (TextLineDto textLine : region.textLines()) {
                    if (textLine.id() != null) {
                        byId.put(textLine.id(), textLine);
                    }
                }
            }
            collectTextLines(region.nestedRegions(), byId);
        }
    }

    private void collectRegions(List<RegionDto> regions, Map<String, RegionDto> byId) {
        if (regions == null) {
            return;
        }
        for (RegionDto region : regions) {
            if (region.id() != null) {
                byId.put(region.id(), region);
            }
            collectRegions(region.nestedRegions(), byId);
        }
    }

    private PageDto copyPageDto(PageDto source, List<RegionDto> regions) {
        return new PageDto(
                source.imageFilename(),
                source.imageWidth(),
                source.imageHeight(),
                source.imageXResolution(),
                source.imageYResolution(),
                source.imageResolutionUnit(),
                source.metadata(),
                source.pcGtsId(),
                source.type(),
                source.custom(),
                source.orientation(),
                source.primaryLanguage(),
                source.secondaryLanguage(),
                source.primaryScript(),
                source.secondaryScript(),
                source.readingDirection(),
                source.textLineOrder(),
                source.confidence(),
                source.border(),
                source.printSpace(),
                regions,
                source.readingOrder(),
                source.alternativeImages(),
                source.labels(),
                source.userDefined(),
                source.textStyle(),
                source.layers(),
                source.relations(),
                source.formatVersion()
        );
    }

    private RegionDto copyRegionDto(RegionDto source, List<TextLineDto> textLines, List<RegionDto> nestedRegions) {
        return new RegionDto(
                source.id(),
                source.kind(),
                source.coords(),
                textLines,
                source.textContentVariants(),
                source.alternativeImages(),
                source.labels(),
                source.userDefined(),
                source.roles(),
                source.grid(),
                source.textStyle(),
                source.type(),
                source.orientation(),
                source.textColour(),
                source.bgColour(),
                source.reverseVideo(),
                source.fontSize(),
                source.fontFamily(),
                source.serif(),
                source.monospace(),
                source.xHeight(),
                source.leading(),
                source.kerning(),
                source.align(),
                source.textColourRgb(),
                source.bgColourRgb(),
                source.readingDirection(),
                source.readingOrientation(),
                source.textLineOrder(),
                source.indented(),
                source.primaryLanguage(),
                source.secondaryLanguage(),
                source.primaryScript(),
                source.secondaryScript(),
                source.production(),
                source.numColours(),
                source.embText(),
                source.colourDepth(),
                source.lineColour(),
                source.lineSeparators(),
                source.rows(),
                source.columns(),
                source.colour(),
                source.penColour(),
                source.borderPresent(),
                nestedRegions,
                source.confidence(),
                source.custom(),
                source.comments(),
                source.continuation()
        );
    }
}
