package com.composum.platform.workflow.model;

import com.composum.platform.models.simple.LoadedModel;
import com.composum.platform.models.simple.LoadedResource;
import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import org.apache.sling.api.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

public class Workflow extends LoadedModel {

    private static final Logger LOG = LoggerFactory.getLogger(Workflow.class);

    private final WorkflowService service;

    public class Transition {

        public final WorkflowTask from;
        public final WorkflowTaskTemplate.Option option;
        public final WorkflowTask to;

        public Transition(WorkflowTask from, WorkflowTaskTemplate.Option option, WorkflowTask to) {
            this.from = from;
            this.option = option;
            this.to = to;
        }
    }

    protected LinkedHashMap<String, WorkflowTask> tasks = new LinkedHashMap<>();
    protected ArrayList<Transition> transitions = new ArrayList<>();
    protected ArrayList<WorkflowTaskInstance> openTasks = new ArrayList<>();
    protected ArrayList<WorkflowTaskInstance> instances = new ArrayList<>();
    protected boolean workflowTemplate = true;
    protected Calendar created;
    protected Calendar finished;
    protected WorkflowTask firstTask;
    protected WorkflowTaskInstance lastTask;

    /**
     * @return 'true' if the workflow is built from task templates only
     */
    public boolean isTemplate() {
        return workflowTemplate;
    }

    /**
     * @return 'true' if workflow contains open task instances
     */
    public boolean isOpen() {
        return !isTemplate() && !openTasks.isEmpty();
    }

    /**
     * @return 'true' if workflow all task instances are finished
     */
    public boolean isFinished() {
        return !isTemplate() && openTasks.isEmpty();
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }

    public Workflow(WorkflowService service) {
        this.service = service;
    }

    public LinkedHashMap<String, WorkflowTask> getTasks() {
        return tasks;
    }

    public boolean containsTasks(WorkflowTask task) {
        return tasks.containsKey(task instanceof WorkflowTaskInstance ? task.getName() : task.getPath());
    }

    public List<WorkflowTaskInstance> getInstances() {
        return instances;
    }

    public WorkflowTask getFirstTask() {
        return firstTask;
    }

    public WorkflowTaskInstance getLastTask() {
        return lastTask;
    }

    public Calendar getCreationDate() {
        return created;
    }

    public Calendar getFinishedDate() {
        return finished;
    }

    public List<WorkflowTaskInstance> getOpenTasks() {
        return openTasks;
    }

    public List<Transition> getTransitions() {
        return transitions;
    }

    public List<Transition> getTransitions(WorkflowTask task) {
        List<Transition> subset = new ArrayList<>();
        for (Transition transition : transitions) {
            if (transition.from == task) {
                subset.add(transition);
            }
        }
        return subset;
    }

    public void initialize(BeanContext context, Resource taskResource) {
        super.initialize(context, new LoadedResource(taskResource));
        try {
            WorkflowTaskInstance taskInstance = findInitialTaskInstance(context);
            if (taskInstance != null) {
                workflowTemplate = false;
                firstTask = taskInstance;
                created = taskInstance.getCreated();
                buildWorkflowFromInstances(context, taskInstance);
            } else {
                buildWorkflowFromTemplates(context);
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    protected void addTask(WorkflowTask task) {
        String key = task instanceof WorkflowTaskInstance ? task.getName() : task.getPath();
        if (!tasks.containsKey(key)) {
            tasks.put(key, task);
        } else {
            throw new IllegalStateException("workflow contains key '" + key + "' twice; maybe a configuration loop");
        }
    }

    // build from instances

    /**
     * @return the initial (first) instance of a running workflow;
     * 'null' if the resource doesn't reference a workflow instance
     */
    protected WorkflowTaskInstance findInitialTaskInstance(BeanContext context) {
        WorkflowTaskInstance task = service.getInstance(context, resource.getPath());
        if (task != null) {
            WorkflowTaskInstance prevTask;
            while ((prevTask = task.getPreviousTask()) != null) {
                task = prevTask;
            }
        }
        return task;
    }

    /**
     * scan instances and configuration assuming that the resource references any of the instances
     */
    protected void buildWorkflowFromInstances(@Nonnull final BeanContext context,
                                              @Nonnull final WorkflowTaskInstance task) {
        addTask(task);
        if (!instances.contains(task)) {
            instances.add(task);
        }
        if (task.getState() != WorkflowTaskInstance.State.finished) {
            openTasks.add(task);
        } else {
            Calendar taskFinished = task.getFinished();
            if (taskFinished != null && (finished == null || finished.before(taskFinished))) {
                finished = taskFinished;
                lastTask = task;
            }
        }
        WorkflowTaskTemplate template = task.getTemplate();
        WorkflowTaskInstance nextTask = task.getNextTask();
        WorkflowTaskTemplate.Option chosenOption = null;
        if (nextTask != null) {
            String optionKey = task.getChosenOption();
            chosenOption = template.getOption(optionKey);
            transitions.add(new Transition(task, chosenOption, nextTask));
            buildWorkflowFromInstances(context, nextTask);
        }
        for (WorkflowTaskTemplate.Option option : template.getOptions()) {
            if (!option.equals(chosenOption)) {
                addOption(context, task, option);
            }
        }
    }

    // build from templates

    /**
     * scan configuration assuming that the resource references the first task template of the workflow
     */
    protected void buildWorkflowFromTemplates(@Nonnull final BeanContext context) {
        WorkflowTaskTemplate task = service.getTemplate(context, resource.getPath());
        if (task != null) {
            addTask(task);
            firstTask = task;
        }
    }

    protected void buildWorkflowFromTemplates(@Nonnull final BeanContext context,
                                              @Nonnull final WorkflowTaskTemplate task) {
        addTask(task);
        for (WorkflowTaskTemplate.Option option : task.getOptions()) {
            addOption(context, task, option);
        }
    }

    protected void addOption(@Nonnull final BeanContext context,
                             @Nonnull final WorkflowTask task, @Nonnull final WorkflowTaskTemplate.Option option) {
        transitions.add(new Transition(task, option, option.template));
        if (option.template != null) {
            buildWorkflowFromTemplates(context, option.template);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Workflow) {
            Workflow otherWorkflow = (Workflow) other;
            if (isTemplate()) {
                Iterator<String> myKeys = tasks.keySet().iterator();
                Iterator<String> otherKeys = otherWorkflow.tasks.keySet().iterator();
                while (myKeys.hasNext()) {
                    if (!otherKeys.hasNext() || !myKeys.next().equals(otherKeys.next())) {
                        return false;
                    }
                }
                return true;
            } else {
                return getFirstTask().equals(otherWorkflow.getFirstTask());
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getFirstTask().hashCode();
    }
}
