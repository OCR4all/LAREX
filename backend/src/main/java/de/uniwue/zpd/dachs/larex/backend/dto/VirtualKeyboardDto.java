package de.uniwue.zpd.dachs.larex.backend.dto;

import de.uniwue.zpd.dachs.larex.backend.entity.VirtualKeyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class VirtualKeyboardDto {
    private String id;
    private String name;
    private String description;
    private List<String> tags = new ArrayList<>();
    private int cols;
    private int rows;
    private String themeId;
    private List<KeyboardItemDto> items = new ArrayList<>();

    public VirtualKeyboardDto() {
    }

    public VirtualKeyboardDto(VirtualKeyboard keyboard) {
        this.id = keyboard.getId();
        this.name = keyboard.getName();
        this.description = keyboard.getDescription();
        if (keyboard.getTags() != null) {
            this.tags = new ArrayList<>(keyboard.getTags());
        }
        this.cols = keyboard.getCols();
        this.rows = keyboard.getRows();
        this.themeId = keyboard.getThemeId();
        if (keyboard.getItems() != null) {
            this.items = keyboard.getItems().stream()
                    .map(KeyboardItemDto::new)
                    .collect(Collectors.toList());
        }
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public int getCols() {
        return cols;
    }

    public void setCols(int cols) {
        this.cols = cols;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    public String getThemeId() {
        return themeId;
    }

    public void setThemeId(String themeId) {
        this.themeId = themeId;
    }

    public List<KeyboardItemDto> getItems() {
        return items;
    }

    public void setItems(List<KeyboardItemDto> items) {
        this.items = items;
    }
}
