package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class NormalizationReplacementRule {

    @Column(name = "search_value", nullable = false, columnDefinition = "TEXT")
    private String search;

    @Column(name = "replacement_value", nullable = false, columnDefinition = "TEXT")
    private String replacement = "";

    @Column(name = "regex_rule", nullable = false)
    private boolean regex;

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public String getReplacement() {
        return replacement;
    }

    public void setReplacement(String replacement) {
        this.replacement = replacement == null ? "" : replacement;
    }

    public boolean isRegex() {
        return regex;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }
}
