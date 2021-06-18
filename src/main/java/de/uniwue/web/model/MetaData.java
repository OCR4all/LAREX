package de.uniwue.web.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

public class MetaData {
    @JsonProperty("creator")
    protected String creator;
    @JsonProperty("comments")
    protected String comments;
    @JsonProperty("externalRef")
    protected String externalRef;
    @JsonProperty("creationTime")
    protected Date creationTime;
    @JsonProperty("lastModificationTime")
    protected Date lastModificationTime;

    @JsonCreator
    public MetaData(
            @JsonProperty("creator") String creator,
            @JsonProperty("comments") String comments,
            @JsonProperty("externalRef") String externalRef,
            @JsonProperty("creationTime") Date creationTime,
            @JsonProperty("lastModificationTime") Date lastModificationTime
    ){
        this.creator = creator;
        this.comments = comments;
        this.externalRef = externalRef;
        this.creationTime = creationTime;
        this.lastModificationTime = lastModificationTime;
    }

    public MetaData(org.primaresearch.dla.page.metadata.MetaData metadata){
        this.creator = metadata.getCreator();
        this.comments = metadata.getComments();
        this.externalRef = metadata.getExternalRef();
        this.creationTime = metadata.getCreationTime();
        this.lastModificationTime = metadata.getLastModificationTime();
    }

    public MetaData(){
        this.creator = null;
        this.comments = null;
        this.externalRef = null;
        this.creationTime = null;
        this.lastModificationTime = null;
    }

    public String getCreator(){ return creator; }
    public String getComments(){ return comments; }
    public String getExternalRef() { return externalRef; }
    public Date getCreationTime() { return creationTime; }
    public Date getLastModificationTime() { return lastModificationTime; }
}
