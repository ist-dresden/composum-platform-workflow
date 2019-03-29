package com.composum.platform.workflow.model;

import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import com.composum.sling.core.bean.BeanFactory;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;

import static com.composum.platform.workflow.model.Workflow.RA_WORKFLOW_CONDENSE;

/**
 * a model class of a workflow task instance
 * <p>
 * such a tasks instance is declared by its template in the repository and is identified by its unique node name (UUID)
 * independent from the path in the repository (the instance is moved according to the tasks state during processing)
 * </p><p>
 * BeanFactory - an instance of this abstract class can only be generated by the WorkflowService
 * </p>
 */
@BeanFactory(serviceClass = WorkflowService.class)
public abstract class WorkflowTaskInstance extends WorkflowTask {

    public enum State {pending, running, finished}

    /** the resource type to identify a resource as workflow task instance */
    public static final String INSTANCE_TYPE = "composum/platform/workflow/task/instance";

    public static final String PN_INITIATOR = "initiator";
    public static final String PN_CHOSEN_OPTION = "chosenOption";
    public static final String PN_PREVIOUS = "previous";
    public static final String PN_NEXT = "next";

    public static final String PN_EXECUTED = "executed";
    public static final String PN_EXECUTED_BY = PN_EXECUTED + "By";
    public static final String PN_CANCELLED = "cancelled";
    public static final String PN_CANCELLED_BY = PN_CANCELLED + "By";
    public static final String PN_FINISHED = "finished";
    public static final String PN_FINISHED_BY = PN_FINISHED + "By";

    protected final WorkflowTaskTemplate template;
    protected final State state;

    private transient Collection<Option> options;
    private transient WorkflowTaskInstance previousTask;
    private transient WorkflowTaskInstance nextTask;

    protected WorkflowTaskInstance(@Nonnull final WorkflowTaskTemplate template, @Nonnull final State state) {
        this.template = template;
        this.state = state;
    }

    @Override
    public void initialize(@Nonnull final BeanContext context, @Nonnull final Resource resource) {
        super.initialize(context, resource);
    }

    /** must be implemented by the service */
    @Nullable
    protected abstract WorkflowTaskInstance getTask(String propertyName);

    @Nonnull
    public String getResourceType() {
        return INSTANCE_TYPE;
    }

    @Nonnull
    public WorkflowTaskTemplate getTemplate() {
        return template;
    }

    // task instance status

    @Nonnull
    public State getState() {
        return state;
    }

    /**
     * @return 'true' if the task is finished but by cancelling the task (no action processing done)
     */
    public boolean isCancelled() {
        return getState() == State.finished && getProperty(PN_CANCELLED, Calendar.class) != null;
    }

    /**
     * @return the id of the user who has executed or finished (cancelled) the task
     */
    @Nonnull
    public String getUserId() {
        return getProperty(PN_EXECUTED_BY, getProperty(PN_FINISHED_BY, getAssignee()));
    }

    /**
     * @return the user id of the workflow 'initiator' (the user who has created the first task instance of the workflow)
     */
    @Nonnull
    public String getInitiator() {
        return getProperty(PN_INITIATOR, "");
    }

    /**
     * @return the key of the chosen option if the task processing is done ("" -> default option)
     */
    @Nonnull
    public String getChosenOption() {
        return getProperty(PN_CHOSEN_OPTION, "");
    }

    /**
     * @return the last relevant date (executed, finished, created) as formatted string
     */
    @Nonnull
    public String getDate() {
        return getDate(PN_EXECUTED, PN_FINISHED, JcrConstants.JCR_CREATED);
    }

    @Nullable
    public Calendar getFinished() {
        return getProperty(PN_FINISHED, Calendar.class);
    }

    // driven by template

    @Nonnull
    public String[] getCategory() {
        return getTemplate().getCategory();
    }

    @Nonnull
    public String getTopic() {
        return getTemplate().getTopic();
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

    /**
     * @return the list of available options from the template (if not finished) or
     * the chosen option only (if finished and 'condense' is requested)
     */
    @Override
    @Nonnull
    public Collection<Option> getOptions() {
        if (options == null) {
            Workflow workflow = getWorkflow();
            SlingHttpServletRequest request = context.getRequest();
            if (workflow != null && request != null &&
                    (Boolean.TRUE.equals(request.getAttribute(RA_WORKFLOW_CONDENSE)))) {
                // a 'condensed' graph doesn't contain options of finished task instances
                String choosenKey = getChosenOption();
                Option choosenOption = getTemplate().getOption(choosenKey);
                options = choosenOption != null
                        ? Collections.singletonList(choosenOption)
                        : getTemplate().getOptions();
            } else {
                options = getTemplate().getOptions();
            }
        }
        return options;
    }

    // Workflow (graph)

    /**
     * @return 'true' if it's allowed to cancel the task processing
     */
    public abstract boolean isCancellingAllowed();

    /**
     * @return 'true' if it's possible to show a workflow graph from this instance
     */
    public abstract boolean isGraphAvailable();

    /**
     * @return the predecessor task instance object if available
     */
    @Nullable
    public WorkflowTaskInstance getPreviousTask() {
        if (previousTask == null) {
            previousTask = getTask(PN_PREVIOUS);
        }
        return previousTask;
    }

    /**
     * @return the successor task instance object if available
     */
    @Nullable
    public WorkflowTaskInstance getNextTask() {
        if (nextTask == null) {
            nextTask = getTask(PN_NEXT);
        }
        return nextTask;
    }

    // Object

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
