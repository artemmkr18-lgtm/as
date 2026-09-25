package pyrock.classes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PyHud {
    private static final List<PyHudElement> elements = new ArrayList<>();

    public PyHudElement add(String name, String icon, double w, double h, double x, double y, boolean showing) {
        PyHudElement elem = new PyHudElement(name, icon, (float) w, (float) h, (float) x, (float) y);
        elem.setShowing(showing);
        elements.add(elem);
        return elem;
    }

    public PyHudElement find(String name) {
        for (PyHudElement e : elements) {
            if (e.getName().equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    public List<PyHudElement> all() {
        return new ArrayList<>(elements);
    }

    public boolean remove(PyHudElement element) {
        return elements.remove(element);
    }
}
