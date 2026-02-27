package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "virtual_keyboards",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_virtual_keyboard_workspace_name", columnNames = {"workspace_id", "name"})
    }
)
@EntityListeners(AuditingEntityListener.class)
public class VirtualKeyboard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "user_id")
    private String userId;

    private int cols;

    private int rows;

    @Column(name = "theme_id")
    private String themeId;

    @ElementCollection
    @CollectionTable(name = "virtual_keyboard_tags", joinColumns = @JoinColumn(name = "virtual_keyboard_id"))
    @Column(name = "tag", nullable = false)
    private List<String> tags = new ArrayList<>();

    @OneToMany(mappedBy = "virtualKeyboard", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<KeyboardItem> items = new ArrayList<>();

    public VirtualKeyboard() {
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

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<KeyboardItem> getItems() {
        return items;
    }

    public void setItems(List<KeyboardItem> items) {
        this.items = items;
    }
    
    public void addItem(KeyboardItem item) {
        items.add(item);
        item.setVirtualKeyboard(this);
    }

    public void removeItem(KeyboardItem item) {
        items.remove(item);
        item.setVirtualKeyboard(null);
    }
}
