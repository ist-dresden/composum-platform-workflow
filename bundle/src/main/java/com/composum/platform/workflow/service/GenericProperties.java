package com.composum.platform.workflow.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.stream.JsonReader;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * a map which is storing data referenced by paths as keys in a hierarchy of nested maps
 */
public class GenericProperties extends HashMap<String, Object> {

    public static final Gson GSON = new GsonBuilder().registerTypeAdapter(Map.class, new InstanceCreator<Map>() {
        @Override
        public Map createInstance(Type type) {
            return new GenericProperties();
        }
    }).create();

    public GenericProperties() {
        super();
    }

    public GenericProperties(Map<String, Object> properties) {
        super(properties);
    }

    public GenericProperties(String json) {
        this(GSON.fromJson(json, GenericProperties.class));
    }

    public GenericProperties(JsonReader reader) {
        this((GenericProperties) GSON.fromJson(reader, GenericProperties.class));
    }

    /**
     * @return the entire map as a JSON object string
     */
    @Override
    public String toString() {
        return GSON.toJson(this);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T get(@Nonnull String path, Class<? extends T> type) {
        return (T) get(this, path, false);
    }

    @Nonnull
    public <T> T get(@Nonnull String path, @Nonnull T defaultValue) {
        T value = get(this, path, false);
        return value != null ? value : defaultValue;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    protected <T> T get(@Nullable final GenericProperties properties, @Nonnull final String path, boolean create) {
        T value = null;
        if (properties != null) {
            String[] segments = StringUtils.split(path, "/", 2);
            if (segments.length > 1) {
                GenericProperties props = (GenericProperties) properties.get(segments[0]);
                if (props == null && create) {
                    properties.put(segments[0], props = new GenericProperties());
                }
                value = get(props, segments[1], create);
            } else {
                value = (T) properties.get(segments[0]);
            }
        }
        return value;
    }

    @Override
    public Object put(String path, @Nullable Object value) {
        return put(this, path, value);
    }

    protected Object put(@Nonnull final GenericProperties properties,
                         @Nonnull final String path, @Nullable final Object value) {
        String[] segments = StringUtils.split(path, "/", 2);
        if (segments.length > 1) {
            GenericProperties props = (GenericProperties) properties.get(segments[0]);
            if (props == null) {
                properties.put(segments[0], props = new GenericProperties());
            }
            return put(props, segments[1], value);
        } else {
            if (value != null) {
                return properties.put(segments[0], value);
            } else {
                properties.remove(segments[0]);
                return null;
            }
        }
    }

    // provide embedded collections

    @Nonnull
    public List<String> getMulti(String path) {
        List<String> values = get(this, path, true);
        if (values == null) {
            put(path, values = new ArrayList<>());
        }
        return values;
    }

    @Nonnull
    public GenericProperties getMap(String path) {
        GenericProperties map = get(this, path, true);
        if (map == null) {
            put(path, map = new GenericProperties());
        }
        return map;
    }

    @Nonnull
    public List<GenericProperties> getList(String path) {
        List<GenericProperties> list = get(this, path, true);
        if (list == null) {
            put(path, list = new ArrayList<>());
        }
        return list;
    }
}
