package de.uniwue.zpd.dachs.larex.backend.service.annotation.mapping;

import com.maxnth.page4j.geometry.Point;
import com.maxnth.page4j.geometry.Polygon;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.regex.Pattern;

final class GridPointsText {

    private static final String COORDINATE = "(?:0|-?[1-9]\\d{0,9})";
    private static final String POINT = COORDINATE + "," + COORDINATE;
    private static final Pattern POINTS = Pattern.compile(POINT + "(?: " + POINT + ")*");

    private GridPointsText() {
    }

    static Polygon parse(String text) {
        if (text == null || !POINTS.matcher(text).matches()) {
            return null;
        }
        List<Point> points = new ArrayList<>();
        try {
            for (String pair : text.split(" ")) {
                int comma = pair.indexOf(',');
                points.add(new Point(Integer.parseInt(pair.substring(0, comma)), Integer.parseInt(pair.substring(comma + 1))));
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return new Polygon(points);
    }

    static String format(Polygon polygon) {
        StringJoiner joiner = new StringJoiner(" ");
        for (Point point : polygon.points()) {
            joiner.add(point.x() + "," + point.y());
        }
        return joiner.toString();
    }
}
