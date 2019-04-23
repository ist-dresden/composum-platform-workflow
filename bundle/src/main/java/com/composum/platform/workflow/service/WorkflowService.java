package com.composum.platform.workflow.service;

import com.composum.platform.workflow.model.Workflow;
import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.sling.core.BeanContext;
import com.composum.sling.core.bean.SlingBeanFactory;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public interface WorkflowService extends SlingBeanFactory {

    /** meta data properties for placeholder replacement */
    String META_USER_ID = "userId";
    String META_OPTION = "option";

    /** the template or option data value to trigger removal of this data during task processing */
    String DATA_KEY_REMOVE = "@{remove}";

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

    /**
     * retrieves the workflow which contains the resource as task
     *
     * @param context  the current request context
     * @param resource the task resource
     */
    @Nullable
    Workflow getWorkflow(@Nonnull final BeanContext context, @Nonnull final Resource resource);

    /**
     * find all open workflows which are initiated (started; implicit or explicit) by one user
     *
     * @param context the current request context
     * @param userId  the id of the user
     */
    @Nonnull
    Collection<Workflow> findInitiatedOpenWorkflows(@Nonnull BeanContext context, @Nonnull String userId);

    /**
     * find all available workflows which are initiated (started) by one user
     *
     * @param context the current request context
     * @param userId  the id of the user
     */
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
     * loads a task instance from the repository
     *
     * @param context  the current request context
     * @param pathOrId the repository path or the instance id (name) of the instance
     */
    @Nullable
    WorkflowTaskInstance getInstance(@Nonnull BeanContext context, @Nonnull String pathOrId);

    /**
     * loads a task template from the repository
     *
     * @param context the current request context
     * @param path    the repository path of the template
     */
    @Nullable
    WorkflowTaskTemplate getTemplate(@Nonnull BeanContext context, @Nonnull String path);

    /**
     * @return the current state of the task instance referenced by path or id
     */
    @Nullable
    WorkflowTaskInstance.State getState(@Nonnull BeanContext context, @Nonnull String pathOrId);

    /**
     * @return the tenant id derived from the hint (task instance path or another path or a tenant parameter)
     */
    String getTenantId(@Nonnull BeanContext context, @Nullable String hint);

    /**
     * builds a new (or the next) task instance (for the 'inbox')
     *
     * @param context      the current request context
     * @param requestData  the meta data extracted from the request (tenant, assignee, comment)
     * @param previousTask the path of the previous instance which has triggered the new task (optional)
     * @param taskTemplate the path of the template of the new task
     * @param target       the list of target resource paths
     * @param data         the properties for the task ('data' must be named as 'data/key')
     * @return the model of the created instance
     */
    @Nullable
    WorkflowTaskInstance addTask(@Nonnull BeanContext context, @Nonnull ValueMap requestData,
                                 @Nullable String previousTask, @Nonnull String taskTemplate,
                                 @Nonnull List<String> target, @Nonnull ValueMap data);

    /**
     * creates a job for execution of the a task instance (triggered by a user or another job)
     *
     * @param context      the current request context
     * @param requestData  the meta data extracted from the request (tenant, assignee, comment)
     * @param taskInstance the path to the task instance ('inbox' resource)
     * @param option       the users choice for the next workflow step
     * @param data         the data values ('data/...') for the task to execute
     * @return the model of the moved instance
     */
    @Nullable
    WorkflowTaskInstance runTask(@Nonnull BeanContext context, @Nonnull ValueMap requestData,
                                 @Nonnull String taskInstance, @Nullable String option, @Nonnull ValueMap data)
            throws PersistenceException;

    /**
     * finishes the execution of the given task
     *
     * @param context      the current request context
     * @param requestData  the meta data extracted from the request (tenant, assignee, comment)
     * @param taskInstance the path to the task instance ('inbox' resource)
     * @param cancelled    should be 'true' if the workflow execution is cancelled
     * @param data         the values for the task to execute ('data' must be named as 'data/key')
     */
    @Nullable
    WorkflowTaskInstance finishTask(@Nonnull BeanContext context, @Nonnull ValueMap requestData,
                                    @Nonnull String taskInstance, boolean cancelled,
                                    @Nonnull ValueMap data)
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
