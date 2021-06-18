package de.uniwue.web.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Element {
    @JsonProperty("id")
    protected String id;
    @JsonProperty("coords")
    protected Polygon coords;

    @JsonCreator
    public Element(
            @JsonProperty("id") String id,
            @JsonProperty("coords") Polygon coords) {
        this.id = id;
        this.coords = coords;
    }

    public String getId(){
        return id;
    }

    public Polygon getCoords(){
        return coords;
    }
}
