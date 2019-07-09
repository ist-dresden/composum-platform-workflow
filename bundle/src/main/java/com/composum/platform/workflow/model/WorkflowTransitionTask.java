package com.composum.platform.workflow.model;

import com.composum.sling.core.BeanContext;
import com.composum.sling.core.SlingBean;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;

import static com.composum.platform.workflow.model.Workflow.RA_WORKFLOW;
import static com.composum.platform.workflow.model.WorkflowTransition.RA_TRANSITION;

public class WorkflowTransitionTask implements SlingBean {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowTransitionTask.class);

    protected BeanContext context;
    protected Resource resource;

    protected WorkflowTransition transition;

    private transient WorkflowTask task;

    @Override
    public void initialize(BeanContext context, Resource resource) {
        this.context = context;
        this.resource = resource;
        SlingHttpServletRequest request = context.getRequest();
        if (request != null) {
            transition = (WorkflowTransition) request.getAttribute(RA_TRANSITION);
            if (transition != null) {
                // if a transition is rendering this task should be the 'to' task
                task = transition.getToTask();
            }
            if (task == null) {
                Workflow workflow = (Workflow) request.getAttribute(RA_WORKFLOW);
                if (workflow != null) {
                    // if a workflow is rendering and no transition the task must be the first node in the graph
                    task = workflow.getFirstTask();
                }
            }
        }
        LOG.debug("init({}): {} :: {}", resource.getPath(), transition, task);
    }

    public WorkflowTask getTask() {
        return task;
    }

    @Override
    public void initialize(BeanContext context) {
        initialize(context, context.getResource());
    }

    @Nonnull
    @Override
    public String getName() {
        return getTask().getName();
    }

    @Nonnull
    @Override
    public String getPath() {
        return getTask().getPath();
    }

    @Nonnull
    @Override
    public String getType() {
        return getTask().getType();
    }

    public Workflow getWorkflow() {
        return getTask().getWorkflow();
    }

    public boolean isCurrent() {
        return getTask().isCurrent();
    }

    public boolean isWorkflowStart() {
        return getTask().isWorkflowStart() && !isWorkflowLoop();
    }

    public boolean isWorkflowEnd() {
        return getTask().isWorkflowEnd();
    }

    public boolean isWorkflowLoop() {
        WorkflowTask task = getTask();
        return task instanceof WorkflowTaskTemplate && ((WorkflowTaskTemplate)task).isWorkflowLoop();
    }

    public String getTitle() {
        return getTask().getTitle();
    }

    public String getHint() {
        return getTask().getHint();
    }

    public String getTopic() {
        return getTask().getTopic();
    }

    @Nonnull
    public String getAssignee() {
        return getTask().getAssignee();
    }

    public boolean isAutoRun() {
        return getTask().isAutoRun();
    }

    public String getDialog() {
        return getTask().getDialog();
    }

    @Nonnull
    public Collection<WorkflowTask.Option> getOptions() {
        return getTask().getOptions();
    }
}
