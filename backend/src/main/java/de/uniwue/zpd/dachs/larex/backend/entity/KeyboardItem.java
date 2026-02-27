package de.uniwue.zpd.dachs.larex.backend.entity;

import jakarta.persistence.*;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "keyboard_items")
@EntityListeners(AuditingEntityListener.class)
public class KeyboardItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int x;
    private int y;
    private int w;

    @Column(name = "char_value")
    private String charValue;

    @Column(name = "shift_char")
    private String shiftChar;

    @Column(name = "color_class")
    private String colorClass;

    @Column(name = "text_class")
    private String textClass;

    private String description;

    @Column(name = "shift_description")
    private String shiftDescription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "virtual_keyboard_id")
    private VirtualKeyboard virtualKeyboard;

    public KeyboardItem() {
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

    public String getCharValue() {
        return charValue;
    }

    public void setCharValue(String charValue) {
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

    public VirtualKeyboard getVirtualKeyboard() {
        return virtualKeyboard;
    }

    public void setVirtualKeyboard(VirtualKeyboard virtualKeyboard) {
        this.virtualKeyboard = virtualKeyboard;
    }
}
