package com.composum.platform.workflow.model;

import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;

public class WorkflowTaskInstance extends WorkflowTask {

    public enum State {pending, running, finished}

    public static final String PN_INITIATOR = "initiator";
    public static final String PN_TEMPLATE = "template";
    public static final String PN_PREVIOUS = "previous";
    public static final String PN_NEXT = "next";
    public static final String PN_CHOSEN_OPTION = "chosenOption";

    private transient State state;
    private transient WorkflowTaskTemplate template;
    private transient WorkflowTaskInstance previousTask;
    private transient WorkflowTaskInstance nextTask;

    public WorkflowTaskInstance(WorkflowService service) {
        super(service);
    }

    @Override
    public void initialize(BeanContext context, Resource resource) {
        super.initialize(context, resource);
        getTemplate();
    }

    public WorkflowTaskTemplate getTemplate() {
        if (template == null) {
            template = getService().getTemplate(context, getProperty(PN_TEMPLATE, ""));
        }
        return template;
    }

    public State getState() {
        if (state == null) {
            state = getService().getState(this);
        }
        return state;
    }

    public String getTitle() {
        return getTemplate().getTitle();
    }

    public String getHint() {
        return getTemplate().getHint();
    }

    public String getInitiator() {
        return getProperty(PN_INITIATOR, "");
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
    public boolean equals(Object other) {
        return other instanceof WorkflowTaskInstance && getName().equals(((WorkflowTaskInstance) other).getName());
    }

    @Override
    public int hashCode() {
        return getName().hashCode();
    }
}
