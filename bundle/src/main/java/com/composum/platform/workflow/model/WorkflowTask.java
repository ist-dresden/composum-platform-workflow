package com.composum.platform.workflow.model;

import com.composum.platform.models.simple.LoadedModel;
import com.composum.platform.models.simple.LoadedResource;
import com.composum.platform.models.simple.ViewValueMap;
import com.composum.sling.core.BeanContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;

import static com.composum.platform.workflow.model.Workflow.RA_WORKFLOW;

/**
 * the abstract base class of a task instance and a task template
 * <p>
 * this implementation preloads all properties (LoadedModel) because it's possible that the model is instantiated
 * in the context od a service resolver which is normally closed after generation of the instances
 * </p>
 */
@SuppressWarnings("Duplicates")
public abstract class WorkflowTask extends LoadedModel {

    public static final String PN_CATEGORY = "category";
    public static final String PN_TOPIC = "topic";
    public static final String PN_VALIDATION = "validation";

    public static final String PN_TARGET = "target";
    public static final String PN_ASSIGNEE = "assignee";
    public static final String PN_TEMPLATE = "template";

    /** text property names in an 'i18n' context */
    public static final String PN_TITLE = "title";
    public static final String PN_HINT = "hint";
    public static final String PN_HINT_ADDED = "hintAdded";
    public static final String PN_HINT_SELECTED = "hintSelected";

    /** task processing properties */
    public static final String PN_AUTO_RUN = "autoRun";
    public static final String PN_DEFAULT = "default";
    public static final String PN_DIALOG = "dialog";
    public static final String PN_INIT_DIALOG = "initDialog";
    public static final String PN_FORM_TYPE = "formType";

    /** task subnode names (property paths) */
    public static final String PP_OPTIONS = "options";
    public static final String PP_DATA = "data";
    public static final String PP_COMMENTS = "comments";

    /**
     * a processing option of the task (also with preloaded properties)
     */
    public abstract class Option extends LoadedModel {

        protected final String key;
        protected final String topic;
        protected final String hintSelected;
        protected final String formType;
        protected final String templatePath;
        protected final boolean isDefault;

        protected boolean isLoop = false;

        private transient String title;
        private transient String hint;
        private transient ValueMap data;

        public Option(@Nonnull final Resource resource) {
            initialize(WorkflowTask.this.context, resource);
            key = resource.getName();
            templatePath = getProperty(PN_TEMPLATE, "");
            topic = getProperty(PN_TOPIC, "");
            formType = getProperty(PN_FORM_TYPE, "");
            hintSelected = i18n().get(PN_HINT_SELECTED, "");
            isDefault = getProperty(PN_DEFAULT, Boolean.FALSE);
        }

        public WorkflowTask getTask() {
            return WorkflowTask.this;
        }

        /** must be implemented by the service; the template referenced by the option */
        @Nullable
        public abstract WorkflowTaskTemplate getTemplate();

        /** must be implemented by the service; determines the 'loop' state */
        public abstract void setIsLoop(boolean isLoop);

        public boolean isLoop() {
            return isLoop;
        }

        public boolean isDefault() {
            return isDefault;
        }

        @Nonnull // but probably empty
        public String getTopic() {
            return topic;
        }

        @Nonnull // but probably empty
        public String getTitle() {
            if (title == null) {
                WorkflowTaskTemplate template = getTemplate();
                title = i18n().get(PN_TITLE, template != null ? template.getTitle() : "");
            }
            return title;
        }

        @Nonnull // but probably empty
        public String getHint() {
            if (hint == null) {
                WorkflowTaskTemplate template = getTemplate();
                hint = i18n().get(PN_HINT, template != null ? template.getHint() : "");
            }
            return hint;
        }

        @Nonnull // but probably empty
        public String getHintSelected(String alternativeText) {
            return StringUtils.isNotBlank(hintSelected) ? hintSelected : alternativeText;
        }

        public boolean isOptionForm() {
            return StringUtils.isNotBlank(getFormType());
        }

        /**
         * @return the resource type of the component embedded in the dialog if this option is chosen
         */
        @Nonnull // but probably empty
        public String getFormType() {
            return formType;
        }

        /**
         * @return the value map of the 'data' child node of the option
         */
        @Nonnull
        public ValueMap getData() {
            if (data == null) {
                Resource child = getResource().getChild(PP_DATA);
                data = child != null ? child.getValueMap() : new ValueMapDecorator(Collections.emptyMap());
            }
            return data;
        }

        // Object

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

    /** true if the current requests target resource references this model */
    protected boolean isCurrent;

    private transient ValueMap data;

    /**
     * preload all properties to decouple model from resolver
     * (service resolver maybe closed earlier than properties accessed)
     */
    @Override
    public void initialize(BeanContext context, Resource resource) {
        super.initialize(context, new LoadedResource(resource));
        SlingHttpServletRequest request = context.getRequest();
        if (request != null) {
            Resource requested = context.getResolver().resolve(request.getRequestURI());
            isCurrent = resource.getPath().equals(requested.getPath());
        } else {
            isCurrent = false;
        }
    }

    @Nonnull
    public abstract String getResourceType();

    @Nonnull
    public String getAssignee() {
        return getProperty(PN_ASSIGNEE, "");
    }

    @Nonnull
    @SuppressWarnings("ConstantConditions")
    public Calendar getCreated() {
        return getProperty(JcrConstants.JCR_CREATED, Calendar.class);
    }

    /**
     * a template uses its properties ; an instance uses their template
     */
    @Nonnull
    public abstract String[] getCategory();

    // template driven - a template uses its properties; an instance uses its template

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
     * @return a variation of the 'data' prepared for a view (value preparation for displaying)
     */
    @Nonnull
    public ViewValueMap getDataView() {
        return new ViewValueMap(getData());
    }

    // Workflow (graph)

    /**
     * @return 'true' if the current requests target resource references this model
     */
    public boolean isCurrent() {
        return isCurrent;
    }

    /**
     * @return the 'Workflow' instance of the current request (must be stored preemptive)
     */
    @Nullable
    public Workflow getWorkflow() {
        return (Workflow) context.getRequest().getAttribute(RA_WORKFLOW);
    }

    /**
     * @return 'true' if this task is the first task of the workflow
     */
    public boolean isWorkflowStart() {
        Workflow workflow = getWorkflow();
        return workflow != null && this.equals(workflow.getFirstTask());
    }

    /**
     * @return 'true' if this task has no options to extend the workflow during processing
     */
    public boolean isWorkflowEnd() {
        Workflow workflow = getWorkflow();
        return workflow != null && workflow.getTransitions(this).isEmpty();
    }
}
