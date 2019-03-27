package com.composum.platform.workflow.service;

import com.composum.platform.models.simple.MetaData;
import com.composum.platform.workflow.model.Workflow;
import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.sling.core.BeanContext;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public interface WorkflowService {

    /** the task instance reference job property name */
    String PN_TASK_INSTANCE_PATH = "wf.task.instance.path";

    /** the job property 'option' key for the users choice */
    String PN_TASK_OPTION = "wf.task.job.option";

    /** the job property 'initiator' key for the users id */
    String PN_TASK_INITIATOR = "wf.task.job.initiator";

    /** meta data properties for placeholder replacement */
    String META_TENANT_ID = "tenantId";
    String META_USER_ID = "userId";
    String META_OPTION = "option";

    @ObjectClassDefinition(
            name = "Composum Platform Workflow Configuration"
    )
    @interface Configuration {

        @AttributeDefinition(
                name = "Instances Root",
                description = "the repository path for the workflow instances"
        )
        String workflow_root() default "/var/composum/workflow";

        @AttributeDefinition(
                name = "General Folder",
                description = "the name of the store for tasks without tenant"
        )
        String general_path() default "platform";

        @AttributeDefinition(
                name = "Days to keep",
                description = "the number of days to keep finished workflows (default: 100)"
        )
        int workflow_keep_days() default 100;

        @AttributeDefinition(
                name = "Purge cron rule",
                description = "the cron rule for scheduling the workflow purge job (no scheduling if empty)"
        )
        String purge_job_cron() default "0 23 1 * * ?";
    }

    @Nullable
    Configuration getConfig();

    /**
     * retrieves the list of available workflows
     *
     * @param context  the current request context
     * @param tenantId the related tenant (must be selected by the user)
     * @param target   the resource context to retrieve appropriate workflows
     */
    @Nonnull
    Iterator<Workflow> findWorkflows(@Nonnull BeanContext context,
                                     @Nullable String tenantId,
                                     @Nullable Resource target);

    @Nullable
    Workflow getWorkflow(@Nonnull final BeanContext context, @Nonnull final Resource resource);

    @Nonnull
    Collection<Workflow> findInitiatedOpenWorkflows(@Nonnull BeanContext context, @Nonnull String userId);

    @Nonnull
    Collection<Workflow> findInitiatedWorkflows(@Nonnull BeanContext context, @Nonnull String userId);

    /**
     * retrieves the list of tasks in the requested scope
     *
     * @param context  the current request context
     * @param tenantId the related tenant (must be selected by the user)
     * @param scope    the status scope of the retrieval; default: pending
     */
    @Nonnull
    Iterator<WorkflowTaskInstance> findTasks(@Nonnull BeanContext context, @Nullable String tenantId,
                                             @Nullable WorkflowTaskInstance.State scope);

    /**
     * loads a task (template) from the repository
     *
     * @param context the current request context
     * @param path    the repository path ('inbox' or an initial path from an app)
     */
    @Nullable
    WorkflowTaskInstance getInstance(@Nonnull BeanContext context, @Nonnull String path);

    @Nullable
    WorkflowTaskTemplate getTemplate(@Nonnull BeanContext context, @Nonnull String path);

    /**
     * @return the current state of the task referenced by task path or id
     */
    @Nullable
    WorkflowTaskInstance.State getState(@Nonnull BeanContext context, @Nonnull String pathOrId);

    /**
     * @return the tenant id from the task resource path
     */
    String getTenantId(Resource taskResource);

    /**
     * builds a new (or the next) task instance (for the 'inbox')
     *
     * @param context      the current request context
     * @param tenantId     the related tenant (selected by the user or inherited from the previous task)
     * @param previousTask the path of the previous instance which has triggered the new task (optional)
     * @param taskTemplate the path of the template of the new task
     * @param comment      an optional comment added to the task
     * @param data         the properties for the task ('data' must be named as 'data/key')
     * @param metaData     the task meta data from the calling job
     * @return the model of the created instance
     */
    @Nullable
    WorkflowTaskInstance addTask(@Nonnull BeanContext context, @Nullable String tenantId,
                                 @Nullable String previousTask, @Nonnull String taskTemplate,
                                 @Nullable final String comment, @Nullable Map<String, Object> data,
                                 @Nullable final MetaData metaData);

    /**
     * creates a job for execution of the a task instance (triggered by a user or another job)
     *
     * @param context      the current request context
     * @param taskInstance the path to the task instance ('inbox' resource)
     * @param option       the users choice for the next workflow step
     * @param comment      an optional comment added to the task
     * @param data         the values for the task to execute ('data' must be named as 'data/key')
     * @param metaData     the task meta data from the calling job
     * @return the model of the moved instance
     */
    @Nullable
    WorkflowTaskInstance runTask(@Nonnull BeanContext context, @Nonnull String taskInstance,
                                 @Nullable String option, @Nullable String comment, @Nullable Map<String, Object> data,
                                 @Nullable MetaData metaData)
            throws PersistenceException;

    /**
     * finishes the execution of the given task
     *
     * @param context          the current request context
     * @param taskInstancePath the path to the task instance ('inbox' resource)
     * @param cancelled        should be 'true' if the workflow execution is cancelled
     * @param comment          an optional comment added to the task
     * @param data             the values for the task to execute ('data' must be named as 'data/key')
     * @param actionMetaData   the task meta data from the calling service
     */
    @Nullable
    WorkflowTaskInstance finishTask(@Nonnull BeanContext context,
                                    @Nonnull String taskInstancePath, boolean cancelled,
                                    @Nullable String comment, @Nullable Map<String, Object> data,
                                    @Nullable MetaData actionMetaData)
            throws PersistenceException;

    /**
     * removes a task from the task store
     *
     * @param context      the current request context
     * @param instancePath the path to the task instance
     */
    void removeTask(@Nonnull BeanContext context, @Nonnull String instancePath)
            throws PersistenceException;

    /**
     * removes all tasks of workflows finished before the date 'daysFinished' in the past
     *
     * @param context    the current request context (must have all privileges to remove tasks)
     * @param daysToKeep the number of days to keep finished workflows
     */
    void purgeTasks(@Nonnull BeanContext context, int daysToKeep)
            throws PersistenceException;
}
