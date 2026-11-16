package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapper;

import com.maxnth.page4j.geometry.Point;
import com.maxnth.page4j.geometry.Polygon;
import com.maxnth.page4j.model.Metadata;
import com.maxnth.page4j.model.Page;
import com.maxnth.page4j.model.TextRegionBuilder;
import com.maxnth.page4j.model.value.PageDateTime;
import com.maxnth.page4j.pagexml.PageXmlVersion;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnnotationMapperThreadSafetyTest {

    @Test
    void sharedMappersPreserveCoordinatesAcrossConcurrentPageSizes() throws Exception {
        Page4jToDtoMapper toDtoMapper = new Page4jToDtoMapper();
        DtoToPage4jMapper toPage4jMapper = new DtoToPage4jMapper();
        CyclicBarrier startBarrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> smallPage = executor.submit(mappingTask(
                toDtoMapper,
                toPage4jMapper,
                startBarrier,
                1000,
                2000,
                100,
                200
            ));
            Future<Void> largePage = executor.submit(mappingTask(
                toDtoMapper,
                toPage4jMapper,
                startBarrier,
                4000,
                5000,
                3000,
                4000
            ));

            smallPage.get();
            largePage.get();
        } finally {
            executor.shutdownNow();
        }
    }

    private Callable<Void> mappingTask(
        Page4jToDtoMapper toDtoMapper,
        DtoToPage4jMapper toPage4jMapper,
        CyclicBarrier startBarrier,
        int imageWidth,
        int imageHeight,
        int pointX,
        int pointY
    ) {
        return () -> {
            Page source = page(imageWidth, imageHeight, pointX, pointY);
            startBarrier.await();

            for (int iteration = 0; iteration < 250; iteration++) {
                var dto = toDtoMapper.toDto(source, PageXmlVersion.V2019_07_15);
                var mappedPoint = dto.regions().getFirst().coords().points().getFirst();

                assertEquals(CoordinateUtils.pixelToWorldX(pointX, imageWidth), mappedPoint.x());
                assertEquals(CoordinateUtils.pixelToWorldY(pointY, imageHeight), mappedPoint.y());

                Page roundTripped = toPage4jMapper.toPage4j(dto);
                var roundTrippedPoint = roundTripped.regions().getFirst().coords().points().getFirst();
                assertEquals(pointX, roundTrippedPoint.x());
                assertEquals(pointY, roundTrippedPoint.y());
            }
            return null;
        };
    }

    private Page page(int imageWidth, int imageHeight, int pointX, int pointY) {
        return Page.builder()
            .metadata(new Metadata(null, "", PageDateTime.now(), PageDateTime.now(), null, List.of(), List.of()))
            .imageFilename("image.png")
            .imageWidth(imageWidth)
            .imageHeight(imageHeight)
            .regions(List.of(TextRegionBuilder.builder()
                .id("r1")
                .coords(Polygon.of(
                    new Point(pointX, pointY),
                    new Point(pointX + 10, pointY),
                    new Point(pointX + 10, pointY + 10),
                    new Point(pointX, pointY + 10)))
                .build()))
            .build();
    }
}
