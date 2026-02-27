package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.KeyboardItem;

public class KeyboardItemDto {
    private Long id;
    private int x;
    private int y;
    private int w;
    private String charValue;
    private String shiftChar;
    private String colorClass;
    private String textClass;
    private String description;
    private String shiftDescription;

    public KeyboardItemDto() {
    }

    public KeyboardItemDto(KeyboardItem item) {
        this.id = item.getId();
        this.x = item.getX();
        this.y = item.getY();
        this.w = item.getW();
        this.charValue = item.getCharValue();
        this.shiftChar = item.getShiftChar();
        this.colorClass = item.getColorClass();
        this.textClass = item.getTextClass();
        this.description = item.getDescription();
        this.shiftDescription = item.getShiftDescription();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getW() {
        return w;
    }

    public void setW(int w) {
        this.w = w;
    }

    public String getChar() {
        return charValue;
    }

    public void setChar(String charValue) {
        this.charValue = charValue;
    }

    public String getShiftChar() {
        return shiftChar;
    }

    public void setShiftChar(String shiftChar) {
        this.shiftChar = shiftChar;
    }

    public String getColorClass() {
        return colorClass;
    }

    public void setColorClass(String colorClass) {
        this.colorClass = colorClass;
    }

    public String getTextClass() {
        return textClass;
    }

    public void setTextClass(String textClass) {
        this.textClass = textClass;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getShiftDescription() {
        return shiftDescription;
    }

    public void setShiftDescription(String shiftDescription) {
        this.shiftDescription = shiftDescription;
    }
}
