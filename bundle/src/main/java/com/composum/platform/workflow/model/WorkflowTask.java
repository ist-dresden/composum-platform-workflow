package com.composum.platform.workflow.model;

import com.composum.platform.models.simple.LoadedModel;
import com.composum.platform.models.simple.LoadedResource;
import com.composum.platform.models.simple.ViewValueMap;
import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.composum.platform.workflow.model.Workflow.RA_WORKFLOW;

/**
 * the abstract base class of a task instance and a task template
 */
public abstract class WorkflowTask extends LoadedModel {

    public static final String PN_TOPIC = "topic";
    public static final String PN_CATEGORY = "category";
    public static final String PN_ASSIGNEE = "assignee";

    public static final String PN_TITLE = "title";
    public static final String PN_HINT = "hint";
    public static final String PN_HINT_ADDED = "hintAdded";
    public static final String PN_HINT_SELECTED = "hintSelected";
    public static final String PN_DIALOG = "dialog";
    public static final String PN_AUTO_RUN = "autoRun";

    public static final String PP_OPTIONS = "options";
    public static final String PN_DEFAULT = "default";
    public static final String PN_FORM_TYPE = "formType";

    public static final String PP_DATA = "data";
    public static final String PP_COMMENTS = "comments";

    private transient WorkflowService service;

    private transient ValueMap data;
    private transient String dataHint;
    public static final String PN_TEMPLATE = "template";

    public class Option extends LoadedModel {

        protected final String key;
        protected final String topic;
        protected final String title;
        protected final String hint;
        protected final String hintSelected;
        protected final String formType;
        protected final String templatePath;
        protected final boolean isDefault;

        private boolean isLoop = false;

        private transient WorkflowTaskTemplate template;

        public Option(@Nonnull final Resource resource) {
            initialize(WorkflowTask.this.context, resource);
            key = resource.getName();
            templatePath = getProperty(PN_TEMPLATE, "");
            topic = getProperty(PN_TOPIC, "");
            formType = getProperty(PN_FORM_TYPE, "");
            title = i18n().get(PN_TITLE, template != null ? template.getTitle() : "");
            hint = i18n().get(PN_HINT, template != null ? template.getHint() : "");
            hintSelected = i18n().get(PN_HINT_SELECTED, "");
            isDefault = getProperty(PN_DEFAULT, Boolean.FALSE);
        }

        public WorkflowTaskTemplate getTemplate() {
            if (template == null) {
                // lazy load to prevent from stack overflow on task loops
                template = StringUtils.isNotBlank(templatePath)
                        ? getService().getTemplate(context, templatePath) : null;
                if (template != null && isLoop) {
                    template.isLoop = true;
                }
            }
            return template;
        }

        public WorkflowTask getTask() {
            return WorkflowTask.this;
        }

        public String getTopic() {
            return topic;
        }

        public boolean isDefault() {
            return isDefault;
        }

        public String getTitle() {
            return title;
        }

        public String getHint() {
            return hint;
        }

        public String getHintSelected(String alternativeText) {
            return StringUtils.isNotBlank(hintSelected) ? hintSelected : alternativeText;
        }

        public boolean isOptionForm() {
            return StringUtils.isNotBlank(getFormType());
        }

        public String getFormType() {
            return formType;
        }

        public boolean isLoop() {
            return isLoop;
        }

        public void setIsLoop(boolean isLoop) {
            this.isLoop = isLoop;
            getTemplate().isLoop = isLoop;
        }

        @Override
        public boolean equals(Object other) {
            Option option;
            return other instanceof Option && key.equals((option = (Option) other).key) && getTask().equals(option.getTask());
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    /**
     * preload all properties to decouple model from resolver
     * (service resolver maybe closed earlier than properties accessed)
     */
    @Override
    public void initialize(BeanContext context, Resource resource) {
        super.initialize(context, new LoadedResource(resource));
    }

    protected WorkflowService getService() {
        if (service == null) {
            service = context.getService(WorkflowService.class);
        }
        return service;
    }

    @Nonnull
    public abstract String getResourceType();

    public boolean isCurrent() {
        Resource requested = context.getResolver().resolve(context.getRequest().getRequestURI());
        return resource.getPath().equals(requested.getPath());
    }

    public Workflow getWorkflow() {
        return (Workflow) context.getRequest().getAttribute(RA_WORKFLOW);
    }

    public boolean isWorkflowStart() {
        Workflow workflow = getWorkflow();
        return workflow != null && this.equals(workflow.getFirstTask());
    }

    public boolean isWorkflowEnd() {
        Workflow workflow = getWorkflow();
        return workflow != null && workflow.getTransitions(this).isEmpty();
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

    @Nonnull
    public abstract String getTitle();

    @Nonnull
    public abstract String getHint();

    public abstract boolean isAutoRun();

    @Nonnull
    public abstract String getDialog();

    @Nonnull
    public abstract Collection<Option> getOptions();

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

    public ViewValueMap getDataView() {
        return new ViewValueMap(getData());
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
