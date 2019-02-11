package com.composum.platform.workflow.model;

import com.composum.platform.commons.content.ChildValueMap;
import com.composum.platform.models.simple.SimpleModel;
import org.apache.sling.api.resource.ValueMap;

import javax.annotation.Nonnull;

public class WorkflowTask extends SimpleModel {

    public static final String PN_TOPIC = "topic";
    public static final String PN_CATEGORY = "category";
    public static final String PN_ASSIGNEE = "assignee";

    public static final String PP_DATA = "data";

    public static final String TOPIC_GENERIC = "workflow.topic.generic";

    private transient ValueMap data;

    @Nonnull
    public String getTopic() {
        return getProperty(PN_TOPIC, TOPIC_GENERIC);
    }

    @Nonnull
    public String[] getCategory() {
        return getProperty(PN_CATEGORY, new String[0]);
    }

    @Nonnull
    public ValueMap getData() {
        if (data == null) {
            data = new ChildValueMap(getResource().getValueMap(), PP_DATA);
        }
        return data;
    }
}
