package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.BoardTheme;

public class BoardThemeDto {
    private String id;
    private String name;
    private String bgClass;
    private String borderClass;
    private String gridLineClass;
    private String keyBgClass;
    private String keyTextClass;
    private String previewClass;
    private String bgStyle;
    private String keyBgStyle;
    private String keyTextStyle;

    public BoardThemeDto() {
    }

    public BoardThemeDto(BoardTheme theme) {
        this.id = theme.getId();
        this.name = theme.getName();
        this.bgClass = theme.getBgClass();
        this.borderClass = theme.getBorderClass();
        this.gridLineClass = theme.getGridLineClass();
        this.keyBgClass = theme.getKeyBgClass();
        this.keyTextClass = theme.getKeyTextClass();
        this.previewClass = theme.getPreviewClass();
        this.bgStyle = theme.getBgStyle();
        this.keyBgStyle = theme.getKeyBgStyle();
        this.keyTextStyle = theme.getKeyTextStyle();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBgClass() {
        return bgClass;
    }

    public void setBgClass(String bgClass) {
        this.bgClass = bgClass;
    }

    public String getBorderClass() {
        return borderClass;
    }

    public void setBorderClass(String borderClass) {
        this.borderClass = borderClass;
    }

    public String getGridLineClass() {
        return gridLineClass;
    }

    public void setGridLineClass(String gridLineClass) {
        this.gridLineClass = gridLineClass;
    }

    public String getKeyBgClass() {
        return keyBgClass;
    }

    public void setKeyBgClass(String keyBgClass) {
        this.keyBgClass = keyBgClass;
    }

    public String getKeyTextClass() {
        return keyTextClass;
    }

    public void setKeyTextClass(String keyTextClass) {
        this.keyTextClass = keyTextClass;
    }

    public String getPreviewClass() {
        return previewClass;
    }

    public void setPreviewClass(String previewClass) {
        this.previewClass = previewClass;
    }

    public String getBgStyle() {
        return bgStyle;
    }

    public void setBgStyle(String bgStyle) {
        this.bgStyle = bgStyle;
    }

    public String getKeyBgStyle() {
        return keyBgStyle;
    }

    public void setKeyBgStyle(String keyBgStyle) {
        this.keyBgStyle = keyBgStyle;
    }

    public String getKeyTextStyle() {
        return keyTextStyle;
    }

    public void setKeyTextStyle(String keyTextStyle) {
        this.keyTextStyle = keyTextStyle;
    }
}
