package de.uniwue.zpd.dachs.larex.backend.service.annotation.corpus;

import com.maxnth.page4j.model.Page;
import com.maxnth.page4j.pagexml.PageXml;
import com.maxnth.page4j.pagexml.PageXmlReadResult;
import com.maxnth.page4j.pagexml.PageXmlVersion;
import de.uniwue.zpd.dachs.larex.backend.dto.page.core.PageDto;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.DtoToPage4jMapper;
import de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping.Page4jToDtoMapper;
import de.uniwue.zpd.dachs.larex.backend.service.xml.PageXmlConversionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Page4jModelContractTest {

    private static final Set<String> DERIVED_PAGE_FIELDS = Set.of("objects", "parents");

    private static final String PRE_2019 = "PAGE 2009-03-16 to 2010-03-19 only; ingest converts it to a GraphicRegion of type frame";

    private static final Map<String, String> NOT_REPRESENTABLE = Map.ofEntries(
        entry("FrameRegion.alternativeImages", PRE_2019),
        entry("FrameRegion.bgColour", PRE_2019),
        entry("FrameRegion.borderPresent", PRE_2019),
        entry("FrameRegion.comments", PRE_2019),
        entry("FrameRegion.continuation", PRE_2019),
        entry("FrameRegion.coords", PRE_2019),
        entry("FrameRegion.custom", PRE_2019),
        entry("FrameRegion.id", PRE_2019),
        entry("FrameRegion.labels", PRE_2019),
        entry("FrameRegion.regions", PRE_2019),
        entry("FrameRegion.tableCellRole", PRE_2019),
        entry("FrameRegion.userAttributes", PRE_2019)
    );

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final PageXmlConversionService conversionService = new PageXmlConversionService();
    private final Page4jToDtoMapper toDto = new Page4jToDtoMapper();
    private final DtoToPage4jMapper toPage = new DtoToPage4jMapper();

    private final SortedMap<String, SortedSet<String>> populated = new TreeMap<>();
    private final SortedMap<String, SortedSet<String>> changed = new TreeMap<>();

    @TempDir
    Path workDirectory;

    @Test
    void everyModelComponentRoundTripsOrIsListedAsNotRepresentable() throws Exception {
        SortedSet<String> components = new TreeSet<>();
        collectComponents(Page.class, components, new java.util.HashSet<>());

        for (String document : PageCorpus.documents()) {
            PageXmlReadResult read = readable(document);
            if (read == null) {
                continue;
            }
            PageDto dto = toDto.toDto(read.page(), read.version());
            PageDto reloaded = JSON.readValue(JSON.writeValueAsString(dto), PageDto.class);
            compareObject(read.page(), toPage.toPage4j(reloaded), document, true);
        }

        SortedMap<String, String> unexpected = new TreeMap<>();
        for (String component : components) {
            String status = status(component);
            if (status != null && !NOT_REPRESENTABLE.containsKey(component)) {
                unexpected.put(component, status);
            }
        }
        SortedMap<String, String> stale = new TreeMap<>();
        for (String component : NOT_REPRESENTABLE.keySet()) {
            if (!components.contains(component)) {
                stale.put(component, "not a page4j model component");
            } else if (status(component) == null) {
                stale.put(component, "round-trips through the corpus");
            }
        }

        assertEquals(new TreeMap<>(), unexpected,
            "Model components that are lost or never populated by the corpus: map them, add a corpus document, or list them");
        assertEquals(new TreeMap<>(), stale, "Stale NOT_REPRESENTABLE entries");
    }

    private PageXmlReadResult readable(String document) throws Exception {
        if (document.startsWith("invalid/")) {
            return null;
        }
        Path file = workDirectory.resolve(document.replace('/', '_'));
        Files.copy(PageCorpus.DOCUMENTS.resolve(document), file);
        PageXmlVersion version = PageXml.create().read(Files.readAllBytes(file)).version();
        if (version.compareTo(PageXmlConversionService.PRIMARY_VERSION) < 0) {
            conversionService.convertFileInPlace(file, PageXmlConversionService.PRIMARY_PAGE_VERSION);
        }
        return PageXml.create().read(Files.readAllBytes(file));
    }

    private String status(String component) {
        if (changed.containsKey(component)) {
            return "changed in " + String.join(", ", changed.get(component));
        }
        if (!populated.containsKey(component)) {
            return "never populated";
        }
        return null;
    }

    private void compareObject(Object original, Object roundTripped, String document, boolean compare) {
        for (var component : components(original.getClass()).entrySet()) {
            String key = original.getClass().getSimpleName() + "." + component.getKey();
            Object value = read(component.getValue(), original);
            Object other = compare ? read(component.getValue(), roundTripped) : null;
            compareValue(key, value, other, document, compare);
        }
    }

    private void compareValue(String key, Object value, Object other, String document, boolean compare) {
        if (value != null && !(value instanceof List<?> list && list.isEmpty())) {
            populated.computeIfAbsent(key, ignored -> new TreeSet<>()).add(document);
        }
        if (value instanceof List<?> list) {
            boolean aligned = compare && other instanceof List<?> otherList && otherList.size() == list.size();
            if (compare && !aligned) {
                changed(key, document);
            }
            for (int i = 0; i < list.size(); i++) {
                compareElement(key, list.get(i), aligned ? ((List<?>) other).get(i) : null, document, aligned);
            }
            return;
        }
        compareElement(key, value, other, document, compare);
    }

    private void compareElement(String key, Object value, Object other, String document, boolean compare) {
        if (value != null && isModel(value.getClass())) {
            boolean sameType = other != null && other.getClass() == value.getClass();
            if (compare && !sameType) {
                changed(key, document);
            }
            compareObject(value, sameType ? other : null, document, compare && sameType);
            return;
        }
        if (compare && !Objects.equals(value, other)) {
            changed(key, document);
        }
    }

    private void changed(String key, String document) {
        changed.computeIfAbsent(key, ignored -> new TreeSet<>()).add(document);
    }

    private static void collectComponents(Class<?> type, Set<String> out, Set<Class<?>> visited) {
        if (!visited.add(type)) {
            return;
        }
        if (type.isSealed()) {
            for (Class<?> permitted : type.getPermittedSubclasses()) {
                collectComponents(permitted, out, visited);
            }
            return;
        }
        for (var component : components(type).entrySet()) {
            out.add(type.getSimpleName() + "." + component.getKey());
            for (Class<?> nested : modelTypes(component.getValue().getGenericReturnType())) {
                collectComponents(nested, out, visited);
            }
        }
    }

    private static List<Class<?>> modelTypes(Type type) {
        List<Class<?>> result = new ArrayList<>();
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) {
                result.addAll(modelTypes(argument));
            }
        } else if (type instanceof Class<?> cls && isModel(cls)) {
            result.add(cls);
        }
        return result;
    }

    private static boolean isModel(Class<?> type) {
        String name = type.getName();
        return (name.startsWith("com.maxnth.page4j.model.") && !name.startsWith("com.maxnth.page4j.model.value."))
            || name.startsWith("com.maxnth.page4j.geometry.");
    }

    private static Map<String, Method> components(Class<?> type) {
        Map<String, Method> result = new LinkedHashMap<>();
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                result.put(component.getName(), component.getAccessor());
            }
            return result;
        }
        if (type == Page.class) {
            for (Field field : Page.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || DERIVED_PAGE_FIELDS.contains(field.getName())) {
                    continue;
                }
                try {
                    result.put(field.getName(), Page.class.getMethod(field.getName()));
                } catch (NoSuchMethodException e) {
                    throw new IllegalStateException("Page has no accessor for " + field.getName(), e);
                }
            }
        }
        return result;
    }

    private static Object read(Method accessor, Object target) {
        if (target == null) {
            return null;
        }
        try {
            return accessor.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
