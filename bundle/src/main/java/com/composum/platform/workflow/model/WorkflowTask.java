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
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * the abstract base class of a task instance and a task template
 */
public abstract class WorkflowTask extends LoadedModel {

    public static final String PN_TOPIC = "topic";
    public static final String PN_CATEGORY = "category";
    public static final String PN_ASSIGNEE = "assignee";

    public static final String PP_DATA = "data";
    public static final String PP_COMMENTS = "comments";

    protected final WorkflowService service;

    private transient ValueMap data;
    private transient String dataHint;

    public WorkflowTask(WorkflowService service) {
        this.service = service;
    }

    /**
     * preload all properties to decouple model from resolver
     * (service resolver maybe closed earlier than properties accessed)
     */
    @Override
    public void initialize(BeanContext context, Resource resource) {
        super.initialize(context, new LoadedResource(resource));
    }

    /**
     * a template uses its properties ; an instance uses their template
     */
    @Nonnull
    public abstract String[] getCategory();

    /**
     * a template uses its properties ; an instance uses their template
     */
    @Nonnull
    public abstract String getTopic();

    /**
     * @return the creation date as formatted string
     */
    @Nonnull
    public String getDate() {
        return getDate(JcrConstants.JCR_CREATED);
    }

    public Calendar getCreated() {
        return getProperty(JcrConstants.JCR_CREATED, Calendar.class);
    }

    @Nonnull
    public String getAssignee() {
        return getProperty(PN_ASSIGNEE, "");
    }

    /**
     * @return the value map of the 'data' child node
     */
    @Nonnull
    public ValueMap getData() {
        if (data == null) {
            Resource child = getResource().getChild(PP_DATA);
            data = child != null ? child.getValueMap() : new ValueMapDecorator(Collections.emptyMap());
        }
        return data;
    }

    /**
     * @return an HTML table of all data values
     */
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
