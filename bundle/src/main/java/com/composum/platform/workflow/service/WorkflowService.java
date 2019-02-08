package com.composum.platform.workflow.service;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.event.jobs.Job;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface WorkflowService {

    /**
     * loads a task (template) from the repository
     *
     * @param resolver the user/job session
     * @param path     the repository path ('inbox' or an initial path from an app)
     */
    @Nullable
    WorkflowTask getTask(@Nonnull final ResourceResolver resolver, @Nonnull final String path);

    /**
     * restores a task for the properties of a job
     *
     * @param resolver the user session
     * @param job      the job instance
     */
    @Nullable
    WorkflowTask getTask(@Nonnull final ResourceResolver resolver, @Nonnull final Job job);

    /**
     * builds a new (the next) task (for the 'inbox')
     *
     * @param resolver     the user session
     * @param previousTask the task of the job which has triggered the new task (optional)
     * @param nextTask     the template of the new task
     */
    void addTask(@Nonnull final ResourceResolver resolver, @Nullable final String tenantId,
                 @Nullable final WorkflowTask previousTask, @Nonnull final WorkflowTask nextTask);

    /**
     * creates a job for execution of the a task template (triggered by a user or another job)
     *
     * @param resolver     the user/job session
     * @param instancePath the path to the template ('inbox' entry)
     * @param comment      an optional comment added to the task
     */
    void runTask(@Nonnull ResourceResolver resolver,
                 @Nonnull String instancePath, @Nullable String comment);

    /**
     * creates a job for execution of the a task template (triggered by a user or another job)
     *
     * @param resolver     the user/job session
     * @param taskTemplate the template to execute
     * @param comment      an optional comment added to the task
     */
    void runTask(@Nonnull final ResourceResolver resolver,
                 @Nonnull final WorkflowTask taskTemplate, @Nullable final String comment);
}
