package de.uniwue.web.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Element {
    @JsonProperty("id")
    protected String id;
    @JsonProperty("coords")
    protected Polygon coords;
    @JsonProperty("parent")
    protected String parent;

    @JsonCreator
    public Element(
            @JsonProperty("id") String id,
            @JsonProperty("coords") Polygon coords,
            @JsonProperty("parent") String parent) {
        this.id = id;
        this.coords = coords;
        this.parent = parent;
    }

    public Element(
            String id,
            Polygon coords
    ) {
        this.id = id;
        this.coords = coords;
        this.parent = null;
    }

    public String getId(){
        return id;
    }

    public Polygon getCoords(){
        return coords;
    }

    public String getParent(){
        return parent;
    }
}
