package com.composum.platform.workflow.model;

import com.composum.sling.core.BeanContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;

import javax.annotation.Nonnull;
import java.util.Calendar;
import java.util.Collection;

public class WorkflowTaskInstance extends WorkflowTask {

    public enum State {pending, running, finished}

    public static final String INSTANCE_TYPE = "composum/platform/workflow/task/instance";

    public static final String PN_INITIATOR = "initiator";
    public static final String PN_TEMPLATE = "template";
    public static final String PN_PREVIOUS = "previous";
    public static final String PN_NEXT = "next";
    public static final String PN_CHOSEN_OPTION = "chosenOption";
    public static final String PN_EXECUTED = "executed";
    public static final String PN_EXECUTED_BY = PN_EXECUTED + "By";
    public static final String PN_CANCELLED = "cancelled";
    public static final String PN_CANCELLED_BY = PN_CANCELLED + "By";
    public static final String PN_FINISHED = "finished";
    public static final String PN_FINISHED_BY = PN_FINISHED + "By";

    private transient State state;
    private transient WorkflowTaskTemplate template;
    private transient WorkflowTaskInstance previousTask;
    private transient WorkflowTaskInstance nextTask;

    @Override
    public void initialize(BeanContext context, Resource resource) {
        super.initialize(context, resource);
        getTemplate();
        getState();
    }

    @Nonnull
    public String getResourceType() {
        return INSTANCE_TYPE;
    }

    public WorkflowTaskTemplate getTemplate() {
        if (template == null) {
            template = getService().getTemplate(context, getProperty(PN_TEMPLATE, ""));
        }
        return template;
    }

    public State getState() {
        if (state == null) {
            state = getService().getState(context, getName());
        }
        return state;
    }

    /**
     * @return the last relevant date (executed, finished, created) as formatted string
     */
    @Nonnull
    public String getDate() {
        return getDate(PN_EXECUTED, PN_FINISHED, JcrConstants.JCR_CREATED);
    }

    public Calendar getFinished() {
        return getProperty(PN_FINISHED, Calendar.class);
    }

    @Nonnull
    public String getTitle() {
        return getTemplate().getTitle();
    }

    @Nonnull
    public String getHint() {
        return getTemplate().getHint();
    }

    public boolean isAutoRun() {
        return getTemplate().isAutoRun();
    }

    @Nonnull
    public String getDialog() {
        return getTemplate().getDialog();
    }

    @Nonnull
    public String getUserId() {
        return getProperty(PN_EXECUTED_BY, getProperty(PN_FINISHED_BY, getAssignee()));
    }

    public String getInitiator() {
        return getProperty(PN_INITIATOR, "");
    }

    @Nonnull
    public String[] getCategory() {
        return getTemplate().getCategory();
    }

    @Nonnull
    public String getTopic() {
        return getTemplate().getTopic();
    }

    @Override
    @Nonnull
    public Collection<Option> getOptions() {
        return getTemplate().getOptions();
    }

    public WorkflowTaskInstance getPreviousTask() {
        if (previousTask == null) {
            previousTask = getTask(PN_PREVIOUS);
        }
        return previousTask;
    }

    public String getChosenOption() {
        return getProperty(PN_CHOSEN_OPTION, "");
    }

    public WorkflowTaskInstance getNextTask() {
        if (nextTask == null) {
            nextTask = getTask(PN_NEXT);
        }
        return nextTask;
    }

    protected WorkflowTaskInstance getTask(String propertyName) {
        String prevTaskId = getProperty(propertyName, String.class);
        return StringUtils.isNotBlank(prevTaskId) ? getService().getInstance(context, prevTaskId) : null;
    }

    @Override
    public String toString() {
        return getName() + ":" + getProperty(PN_TEMPLATE, "<?>");
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WorkflowTaskInstance && getName().equals(((WorkflowTaskInstance) other).getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
