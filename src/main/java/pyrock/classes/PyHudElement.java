package pyrock.classes;

import ez.minar.system.features.render.HUD;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PyHudElement {
    private String name;
    private String icon;
    private float x;
    private float y;
    private float width;
    private float height;
    private boolean showing = true;

    public PyHudElement(String name, String icon, float width, float height, float x, float y) {
        this.name = name;
        this.icon = icon;
        this.width = width;
        this.height = height;
        this.x = x;
        this.y = y;
    }

    public String getName() {
        return name;
    }

    public String getIcon() {
        return icon;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public boolean isShowing() {
        return showing;
    }

    public void setShowing(boolean showing) {
        this.showing = showing;
    }

    public boolean remove() {
        return true;
    }
}
