package com.composum.platform.workflow.model;

import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import com.composum.sling.core.SlingBean;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;

import java.util.List;

public class WorkflowTransition implements SlingBean {

    protected Workflow.Transition transition;
    protected WorkflowTask task;
    protected boolean chosen;

    protected BeanContext context;
    protected Resource initialResource;
    private transient WorkflowService service;

    public void initialize(BeanContext context, Resource resource) {
        this.context = context;
        this.initialResource = resource;
    }

    @Override
    public void initialize(BeanContext context) {
        initialize(context, context.getResource());
    }

    @Override
    public String getName() {
        return getOption().getName();
    }

    @Override
    public String getPath() {
        return getOption().getPath();
    }

    @Override
    public String getType() {
        return getOption().getType();
    }

    protected WorkflowService getService() {
        if (service == null) {
            service = context.getService(WorkflowService.class);
        }
        return service;
    }

    protected Workflow.Transition getTransition() {
        if (transition == null) {
            Workflow workflow = (Workflow) context.getRequest().getAttribute("workflow");
            if (workflow != null) {
                String suffix = context.getRequest().getRequestPathInfo().getSuffix();
                if (suffix != null) {
                    if (suffix.startsWith("/")) {
                        suffix = suffix.substring(1);
                    }
                    task = workflow.getTask(initialResource);
                    if (task != null) {
                        List<Workflow.Transition> transitions = workflow.getTransitions(task);
                        for (Workflow.Transition transition : transitions) {
                            if (transition.option.key.equals(suffix)) {
                                this.transition = transition;
                                break;
                            }
                        }
                        if (transition != null) {
                            chosen = task instanceof WorkflowTaskInstance &&
                                    transition.option.key.equals(((WorkflowTaskInstance) task).getChosenOption());
                        } else {
                            throw new IllegalArgumentException("unknown option '" + suffix + "'");
                        }
                    } else {
                        throw new IllegalArgumentException("task not found '" + initialResource.getPath() + "'");
                    }
                } else {
                    throw new IllegalArgumentException("invalid option request");
                }
            } else {
                throw new IllegalArgumentException("no workflow found");
            }
        }
        return transition;
    }

    protected WorkflowTask.Option getOption() {
        return getTransition().option;
    }

    public WorkflowTask getTask() {
        getTransition();
        return task;
    }

    public boolean isCurrent() {
        return getTask().isCurrent();
    }

    public boolean isChosen() {
        getTransition();
        return chosen;
    }

    public boolean isWorkflowEnd() {
        return StringUtils.isBlank(getToTaskPath());
    }

    public String getToTaskPath() {
        Workflow.Transition transition = getTransition();
        return transition.to != null ? transition.to.getPath() : "";
    }

    public boolean isDefault() {
        return getOption().isDefault();
    }

    public String getTitle() {
        return getOption().getTitle();
    }

    public String getHint() {
        return getOption().getHint();
    }

    public String getHintSelected(String alternativeText) {
        return getOption().getHintSelected(alternativeText);
    }

    public String getTopic() {
        return getOption().getTopic();
    }

    @Override
    public boolean equals(Object other) {
        WorkflowTask.Option option = getOption();
        return option != null &&
                option.equals(other instanceof WorkflowTransition ? ((WorkflowTransition) other).getOption() : other);
    }

    @Override
    public int hashCode() {
        return getOption().hashCode();
    }
}
