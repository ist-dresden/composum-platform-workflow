package com.composum.platform.workflow.model;

import com.composum.platform.models.simple.LoadedModel;
import com.composum.platform.models.simple.LoadedResource;
import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.composum.platform.workflow.job.GenericWorkflowJob.TOPIC_GENERIC;

public abstract class WorkflowTask extends LoadedModel {

    public static final String PN_TOPIC = "topic";
    public static final String PN_CATEGORY = "category";
    public static final String PN_ASSIGNEE = "assignee";

    public static final String PP_DATA = "data";
    public static final String PP_COMMENTS = "comments";

    private final WorkflowService service;

    private transient ValueMap data;
    private transient String dataHint;

    public WorkflowTask(WorkflowService service) {
        this.service = service;
    }

    @Override
    public void initialize(BeanContext context, Resource resource) {
        super.initialize(context, new LoadedResource(resource));
    }

    @Nonnull
    public WorkflowService getService() {
        return service;
    }

    @Nonnull
    public String getDate() {
        return getDate(JcrConstants.JCR_CREATED);
    }

    @Nonnull
    public String getTopic() {
        return getProperty(PN_TOPIC, TOPIC_GENERIC);
    }

    @Nonnull
    public String[] getCategory() {
        return getProperty(PN_CATEGORY, new String[0]);
    }

    @Nonnull
    public String getAssignee() {
        return getProperty(PN_ASSIGNEE, "");
    }

    @Nonnull
    public ValueMap getData() {
        if (data == null) {
            Resource child = getResource().getChild(PP_DATA);
            data = child != null ? child.getValueMap() : new ValueMapDecorator(Collections.emptyMap());
        }
        return data;
    }

    @Nonnull
    public String getDataHint() {
        if (dataHint == null) {
            StringBuilder builder = new StringBuilder("<table class=\"data-table\"><tbody>");
            ValueMap data = getData();
            List<String> keys = new ArrayList<>(data.keySet());
            Collections.sort(keys);
            for (String key : keys) {
                if (!key.startsWith("jcr:")) {
                    builder.append("<tr><th>").append(key).append("</th><td>").append(data.get(key)).append("</td></tr>");
                }
            }
            builder.append("</tbody></table>");
            dataHint = builder.toString();
        }
        return dataHint;
    }
}
