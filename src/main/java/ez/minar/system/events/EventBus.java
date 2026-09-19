package ez.minar.system.events;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {
    private static class EventListener {
        final Object owner;
        final Method method;
        final EventPriority priority;

        EventListener(Object owner, Method method, EventPriority priority) {
            this.owner = owner;
            this.method = method;
            this.priority = priority;
        }
    }

    private static final Map<Class<? extends Event>, List<EventListener>> listeners =
            new ConcurrentHashMap<>();

    private static final Set<Object> registeredObjects = Collections.newSetFromMap(
            new ConcurrentHashMap<>()
    );

    public static void register(Object object) {
        if (registeredObjects.contains(object)) return;

        registeredObjects.add(object);

        for (Method method : object.getClass().getDeclaredMethods()) {
            if (!method.isAnnotationPresent(EventHandler.class)) continue;
            if (method.getParameterCount() != 1) continue;

            Class<?> paramType = method.getParameterTypes()[0];
            if (!Event.class.isAssignableFrom(paramType)) continue;

            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) paramType;

            EventHandler annotation = method.getAnnotation(EventHandler.class);
            EventPriority priority = annotation.priority();

            method.setAccessible(true);

            listeners.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>())
                    .add(new EventListener(object, method, priority));

            listeners.get(eventClass).sort(
                    Comparator.comparingInt(l -> l.priority.getLevel())
            );
        }
    }

    public static void unregister(Object object) {
        if (!registeredObjects.contains(object)) return;

        registeredObjects.remove(object);

        for (List<EventListener> listenerList : listeners.values()) {
            listenerList.removeIf(listener -> listener.owner == object);
        }
    }

    public static <T extends Event> T post(T event) {
        List<EventListener> eventListeners = listeners.get(event.getClass());

        if (eventListeners == null || eventListeners.isEmpty()) {
            return event;
        }

        for (EventListener listener : eventListeners) {
            if (event.isCancelled()) {
                if (listener.priority.getLevel() < EventPriority.HIGH.getLevel()) {
                    continue;
                }
            }

            try {
                listener.method.invoke(listener.owner, event);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return event;
    }
    public static boolean isRegistered(Object object) {
        return registeredObjects.contains(object);
    }

    public static void clear() {
        listeners.clear();
        registeredObjects.clear();
    }

    public static int getListenerCount(Class<? extends Event> eventClass) {
        List<EventListener> list = listeners.get(eventClass);
        return list == null ? 0 : list.size();
    }
}