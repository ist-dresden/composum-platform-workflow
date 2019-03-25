package com.composum.platform.workflow.model;

import com.composum.platform.models.simple.LoadedModel;
import com.composum.platform.models.simple.LoadedResource;
import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

public class Workflow extends LoadedModel {

    private static final Logger LOG = LoggerFactory.getLogger(Workflow.class);

    public static final String RA_WORKFLOW = "workflow";

    private transient WorkflowService service;

    public class Transition {

        public final WorkflowTask from;
        public final WorkflowTask.Option option;
        public final WorkflowTask to;
        public final String toKey;

        public Transition(WorkflowTask from, WorkflowTask.Option option, WorkflowTask to, String toKey) {
            this.from = from;
            this.option = option;
            this.to = to;
            this.toKey = toKey;
        }

        public String getToKey() {
            return StringUtils.isNotBlank(toKey) ? toKey : to != null ? to.getPath() : null;
        }

        @Override
        public String toString() {
            return super.toString() + "{" + from + " -> " + option.key + "(" + option.isLoop() + ") ->" + to + " }";
        }

        protected void dump(StringBuilder builder, int indent) {
            builder.append(StringUtils.repeat(' ', indent));
            builder.append("- ").append(this).append('\n');
            if (to != null) {
                Workflow.this.dump(builder, indent + 2, to);
            }
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

    private transient List<List<WorkflowTask>> rows;

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

    public boolean isHollow() {
        return tasks.isEmpty();
    }

    public LinkedHashMap<String, WorkflowTask> getTasks() {
        return tasks;
    }

    public WorkflowTask getTask(Resource resource) {
        String path = resource.getPath();
        for (WorkflowTask task : getTasks().values()) {
            if (task.getPath().equals(path)) {
                return task;
            }
        }
        return null;
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
            if (transition.from.equals(task)) {
                subset.add(transition);
            }
        }
        return subset;
    }

    public Transition getTransition(WorkflowTask.Option option) {
        for (Transition transition : transitions) {
            if (transition.option.equals(option)) {
                return transition;
            }
        }
        return null;
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
            LOG.debug("dump\n" + dump());
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    // build from instances

    protected WorkflowService getService() {
        if (service == null) {
            service = context.getService(WorkflowService.class);
        }
        return service;
    }

    /**
     * @return the initial (first) instance of a running workflow;
     * 'null' if the resource doesn't reference a workflow instance
     */
    protected WorkflowTaskInstance findInitialTaskInstance(BeanContext context) {
        WorkflowTaskInstance task = getService().getInstance(context, resource.getPath());
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
        String name = task.getName();
        if (tasks.containsKey(name)) {
            throw new IllegalStateException("workflow contains instance '" + name + "' twice");
        }
        tasks.put(name, task);
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
        WorkflowTask.Option chosenOption = null;
        if (nextTask != null) {
            String optionKey = task.getChosenOption();
            chosenOption = template.getOption(optionKey);
            transitions.add(new Transition(task, chosenOption, nextTask, null));
            buildWorkflowFromInstances(context, nextTask);
        }
        for (WorkflowTask.Option option : template.getOptions()) {
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
        WorkflowTaskTemplate task = getService().getTemplate(context, resource.getPath());
        if (task != null) {
            firstTask = task;
            buildWorkflowFromTemplates(context, task, null);
        }
    }

    protected void buildWorkflowFromTemplates(@Nonnull final BeanContext context,
                                              @Nonnull final WorkflowTaskTemplate task,
                                              @Nullable final WorkflowTask.Option optionToTask) {
        String key = task.getPath();
        if (tasks.containsKey(key)) {
            // this is a loop; it's ok but we should stop building here
            tasks.put(getTemplateKey(task), task);
            if (optionToTask != null) {
                optionToTask.setIsLoop(true);
            } else {
                throw new IllegalStateException("unexpected template loop: '" + task.getPath() + "'");
            }
        } else {
            tasks.put(key, task);
            for (WorkflowTask.Option option : task.getOptions()) {
                addOption(context, task, option);
            }
        }
    }

    protected String getTemplateKey(WorkflowTaskTemplate template) {
        if (template != null) {
            String key = template.getPath();
            if (tasks.containsKey(key)) {
                int i = 0;
                while (tasks.containsKey(key + "#" + i)) i++;
                return key + "#" + i;
            }
        }
        return null;
    }

    protected void addOption(@Nonnull final BeanContext context,
                             @Nonnull final WorkflowTask task, @Nonnull final WorkflowTask.Option option) {
        WorkflowTaskTemplate template = option.getTemplate();
        transitions.add(new Transition(task, option, template, getTemplateKey(template)));
        if (template != null) {
            buildWorkflowFromTemplates(context, template, option);
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

    @Nonnull
    public List<List<WorkflowTask>> getRows() {
        if (rows == null) {
            rows = new ArrayList<>();
            WorkflowTask firstTask = getFirstTask();
            if (firstTask != null) {
                List<WorkflowTask> row = new ArrayList<>();
                row.add(getFirstTask());
                rows.add(row);
                while ((row = buildRow(row)).size() > 0) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    @Nonnull
    protected List<WorkflowTask> buildRow(@Nonnull final List<WorkflowTask> above) {
        List<WorkflowTask> row = new ArrayList<>();
        for (WorkflowTask task : above) {
            for (Transition transition : getTransitions(task)) {
                if (transition.to != null) {
                    row.add(transition.to);
                }
            }
        }
        return row;
    }

    protected String dump() {
        StringBuilder builder = new StringBuilder();
        WorkflowTask task = getFirstTask();
        dump(builder, 0, getFirstTask());
        return builder.toString();
    }

    protected void dump(StringBuilder builder, int indent, WorkflowTask task) {
        boolean isLoop = task instanceof WorkflowTaskTemplate && ((WorkflowTaskTemplate) task).isLoop;
        builder.append(StringUtils.repeat(' ', indent));
        builder.append("# ").append(task).append(isLoop ? " loop!" : " ...").append('\n');
        if (!isLoop) {
            List<Transition> transitions = getTransitions(task);
            for (Transition transition : transitions) {
                transition.dump(builder, indent + 2);
            }
        }
    }
}
