package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapper;

import com.maxnth.page4j.dla.page.Page;
import com.maxnth.page4j.dla.page.io.xml.PageXmlInputOutput;
import com.maxnth.page4j.dla.page.layout.physical.shared.RegionType;
import com.maxnth.page4j.maths.geometry.Polygon;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import de.uniwue.zpd.dachs.larex.backend.util.CoordinateUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
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
    void springSingletonMappersDoNotRetainPerInvocationState() {
        assertEquals(List.of(), instanceFields(Page4jToDtoMapper.class));
        assertEquals(List.of(), instanceFields(DtoToPage4jMapper.class));
    }

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
                var dto = toDtoMapper.toDto(source);
                var mappedPoint = dto.regions().getFirst().coords().points().getFirst();

                assertEquals(CoordinateUtils.pixelToWorldX(pointX, imageWidth), mappedPoint.x());
                assertEquals(CoordinateUtils.pixelToWorldY(pointY, imageHeight), mappedPoint.y());

                Page roundTripped = toPage4jMapper.toPage4j(dto);
                var roundTrippedPoint = roundTripped.getLayout().getRegion(0).getCoords().getPoint(0);
                assertEquals(pointX, roundTrippedPoint.x);
                assertEquals(pointY, roundTrippedPoint.y);
            }
            return null;
        };
    }

    private Page page(int imageWidth, int imageHeight, int pointX, int pointY) throws Exception {
        Page page = new Page(PageXmlInputOutput.getLatestSchemaModel());
        page.setImageFilename("image.png");
        page.getLayout().setSize(imageWidth, imageHeight);

        var region = page.getLayout().createRegion(RegionType.TextRegion, "r1");
        Polygon polygon = new Polygon();
        polygon.addPoint(pointX, pointY);
        polygon.addPoint(pointX + 10, pointY);
        polygon.addPoint(pointX + 10, pointY + 10);
        polygon.addPoint(pointX, pointY + 10);
        region.setCoords(polygon);
        return page;
    }

    private List<String> instanceFields(Class<?> mapperClass) {
        return Arrays.stream(mapperClass.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(Field::getName)
            .sorted()
            .toList();
    }
}
