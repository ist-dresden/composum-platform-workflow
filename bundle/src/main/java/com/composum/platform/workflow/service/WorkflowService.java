package com.composum.platform.workflow.service;

import com.composum.platform.models.simple.MetaData;
import com.composum.platform.workflow.model.Workflow;
import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.sling.core.BeanContext;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;

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

    /**
     * loads a task (template) from the repository
     *
     * @param context  the current request context
     * @param tenantId the related tenant (must be selected by the user)
     */
    @Nonnull
    Iterator<WorkflowTaskInstance> findTasks(@Nonnull BeanContext context, @Nullable String tenantId);

    @Nullable
    Workflow getWorkflow(@Nonnull final BeanContext context, @Nonnull final Resource resource);

    @Nonnull
    Collection<Workflow> findInitiatedOpenWorkflows(@Nonnull BeanContext context, @Nonnull String userId);

    @Nonnull
    Collection<Workflow> findInitiatedWorkflows(@Nonnull BeanContext context, @Nonnull String userId);

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
     * finishes the execution of a workflow by finishing the given task
     *
     * @param context          the current request context
     * @param taskInstancePath the path to the task instance ('inbox' resource)
     * @param cancelled        should be 'true' if the workflow execution is cancelled
     * @param comment          an optional comment added to the task
     * @param data             the values for the task to execute ('data' must be named as 'data/key')
     * @param metaData         the task meta data from the calling job
     * @return the model of the moved instance
     */
    @Nullable
    WorkflowTaskInstance finishTask(@Nonnull BeanContext context,
                                    @Nonnull String taskInstancePath, boolean cancelled,
                                    @Nullable String comment, @Nullable Map<String, Object> data,
                                    @Nullable MetaData metaData)
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
     * @return the current state of the task referenced by task path or id
     */
    @Nullable
    WorkflowTaskInstance.State getState(@Nonnull BeanContext context, @Nonnull String pathOrId);

    /**
     * @return the tenant id from the task resource path
     */
    String getTenantId(Resource taskResource);
}
