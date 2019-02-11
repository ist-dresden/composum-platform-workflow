package com.composum.platform.workflow.service;

import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.jobs.Job;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;

public interface WorkflowService {

    /**
     * loads a task (template) from the repository
     *
     * @param resolver the user/job session
     * @param tenantId the related tenant (must be selected by the user)
     */
    @Nonnull
    Iterator<WorkflowTaskInstance> findTasks(@Nonnull final ResourceResolver resolver, @Nullable final String tenantId);

    /**
     * loads a task (template) from the repository
     *
     * @param resolver the user/job session
     * @param path     the repository path ('inbox' or an initial path from an app)
     */
    @Nullable
    WorkflowTaskInstance getInstance(@Nonnull final ResourceResolver resolver, @Nonnull final String path);

    @Nullable
    WorkflowTaskTemplate getTemplate(@Nonnull ResourceResolver resolver, @Nonnull String path);

    /**
     * restores a task from the properties of a job
     *
     * @param resolver the user session
     * @param job      the job instance
     */
    @Nullable
    WorkflowTaskInstance getInstance(@Nonnull final ResourceResolver resolver, @Nonnull final Job job);

    /**
     * builds a new (or the next) task instance (for the 'inbox') or reconfigures an existing instance as next
     *
     * @param resolver         the user session
     * @param tenantId         the related tenant (selected by the user or inherited from the previous task)
     * @param previousTask     the path of the previous instance which has triggered the new task (optional)
     * @param nextTaskTemplate the path of the template of the new task
     */
    void addTask(@Nonnull final ResourceResolver resolver, @Nullable final String tenantId,
                 @Nullable final String previousTask, @Nonnull final String nextTaskTemplate);

    /**
     * creates a job for execution of the a task instance (triggered by a user or another job)
     *
     * @param resolver     the user/job session
     * @param taskInstance the path to the task instance ('inbox' resource)
     * @param comment      an optional comment added to the task
     * @param data         the values for the task to execute (for the job; provided by the task dialog)
     */
    void runTask(@Nonnull ResourceResolver resolver,
                 @Nonnull String taskInstance, @Nullable Map<String, Object> data, @Nullable String comment);
}
