package com.composum.platform.workflow.service;

import javax.annotation.Nonnull;
import java.util.Collection;

public class WorkflowTask {

    String PN_TOPIC = "topic";
    String PN_CATEGORY = "category";
    String PP_DATA = "data";
    String PP_COMMENTS = "comments";
    String PP_TRACE = "trace";

    String TOPIC_GENERIC = "workflow.topic.generic";

    private final GenericProperties properties;

    public WorkflowTask(GenericProperties properties) {
        this.properties = properties;
    }

    @Nonnull
    public String getTopic() {
        return properties.get(PN_TOPIC, TOPIC_GENERIC);
    }

    @Nonnull
    public Collection<String> getCategory() {
        return properties.getMulti(PN_CATEGORY);
    }

    @Nonnull
    public GenericProperties getData() {
        return properties.getMap(PP_DATA);
    }

    @Nonnull
    public Collection<GenericProperties> getComments() {
        return properties.getList(PP_COMMENTS);
    }

    @Nonnull
    public Collection<GenericProperties> getTrace() {
        return properties.getList(PP_TRACE);
    }

    @Nonnull
    public GenericProperties getProperties() {
        return properties;
    }
}
