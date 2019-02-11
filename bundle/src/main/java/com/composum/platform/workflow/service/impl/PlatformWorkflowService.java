package com.composum.platform.workflow.service.impl;

import com.composum.platform.workflow.model.WorkflowTask;
import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

@Component(
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Platform Workflow Service"
        },
        immediate = true
)
@Designate(ocd = PlatformWorkflowService.Configuration.class)
public class PlatformWorkflowService implements WorkflowService {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformWorkflowService.class);

    /** the task instance reference job property name */
    String PN_TASK_INSTANCE_PATH = "wf.task.instance.path";

    /** the job property 'comment' */
    String PN_TASK_COMMENT = "wf.task.job.comment";

    /** the job property 'data' prefix */
    String PN_TASK_DATA = "wf.task.job.comment";

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
                name = "Inbox Path",
                description = "the path of the inbox"
        )
        String inbox_path() default "inbox";

        @AttributeDefinition(
                name = "Shared Path",
                description = "the name of the store for shared tasks"
        )
        String shared_path() default "shared";
    }

    protected static final Gson GSON = new GsonBuilder().create();

    @Reference
    protected ResourceResolverFactory resolverFactory;

    @Reference
    protected JobManager jobManager;

    protected Configuration config;

    @Activate
    @Modified
    protected void activate(BundleContext bundleContext, Configuration config) {
        this.config = config;
    }

    /**
     * loads a task (template) from the repository
     *
     * @param resolver the user/job session
     * @param tenantId the related tenant (must be selected by the user)
     */
    @Nonnull
    public Iterator<WorkflowTaskInstance> findTasks(@Nonnull final ResourceResolver resolver, @Nullable final String tenantId) {
        ArrayList<WorkflowTaskInstance> tasks = new ArrayList<>();
        Resource folder = getInstanceFolder(resolver, tenantId);
        if (folder != null) {
            for (Resource entry : folder.getChildren()) {
                if (JcrConstants.NT_FILE.equals(entry.getValueMap().get(JcrConstants.JCR_PRIMARYTYPE))) {
                    WorkflowTaskInstance task = getInstance(resolver, entry.getPath());
                    tasks.add(task);
                }
            }
        }
        return tasks.iterator();
    }

    /**
     * loads a task instance from the repository
     *
     * @param resolver the user/job session
     * @param path     the repository path
     */
    @Override
    @Nullable
    public WorkflowTaskInstance getInstance(@Nonnull final ResourceResolver resolver, @Nonnull final String path) {
        return getInstance(resolver, path, WorkflowTaskInstance.class);
    }

    /**
     * loads a task template from the repository
     *
     * @param resolver the user/job session
     * @param path     the repository path to the template resource
     */
    @Override
    @Nullable
    public WorkflowTaskTemplate getTemplate(@Nonnull final ResourceResolver resolver, @Nonnull final String path) {
        return getInstance(resolver, path, WorkflowTaskTemplate.class);
    }

    protected <T extends WorkflowTask> T getInstance(@Nonnull final ResourceResolver resolver,
                                                     @Nonnull final String path,
                                                     @Nonnull Class<T> type) {
        T task = null;
        Resource resource = resolver.getResource(path);
        if (resource != null) {
            try {
                task = (T) type.newInstance();
                task.initialize(new BeanContext.Service(resolver), resource);
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
                task = null;
            }
        }
        return task;
    }

    /**
     * restores a task for the properties of a job
     *
     * @param resolver the user session
     * @param job      the job instance
     */
    @Override
    @Nullable
    public WorkflowTaskInstance getInstance(@Nonnull final ResourceResolver resolver, @Nonnull final Job job) {
        WorkflowTaskInstance task = null;
        String path = (String) job.getProperty(PN_TASK_INSTANCE_PATH);
        if (StringUtils.isNotBlank(path)) {
            task = getInstance(resolver, path);
        }
        return task;
    }

    /**
     * builds a new (the next) task (for the 'inbox')
     *
     * @param resolver         the user session
     * @param tenantId         the related tenant (selected by the user or inherited from the previous task)
     * @param previousTask     the path of the previous instance which has triggered the new task (optional)
     * @param nextTaskTemplate the path of the template of the new task
     */
    @Override
    public void addTask(@Nonnull final ResourceResolver resolver, @Nullable final String tenantId,
                        @Nullable final String previousTask, @Nonnull final String nextTaskTemplate) {
        final WorkflowTaskTemplate template = getInstance(resolver, nextTaskTemplate, WorkflowTaskTemplate.class);
        if (template != null) {
            Map<String, Object> templateProperties = new HashMap<>();
            templateProperties.put(WorkflowTaskInstance.PN_TOPIC, template.getTopic());
            templateProperties.put(WorkflowTaskInstance.PN_CATEGORY, template.getCategory());
            templateProperties.put(WorkflowTaskInstance.PN_ASSIGNEE, template.getPath());
            templateProperties.put(WorkflowTaskInstance.PN_TEMPLATE, template.getPath());
            try (final ResourceResolver serviceResolver = resolverFactory.getServiceResourceResolver(null)) {
                WorkflowTaskInstance task = StringUtils.isNotBlank(previousTask) ? getInstance(resolver, previousTask) : null;
                if (task == null) {
                    // store the task in the 'inbox'...
                    final Resource folder = getInstanceFolder(resolver, tenantId);
                    if (folder != null) {
                        Resource resource = serviceResolver.create(folder, UUID.randomUUID().toString(), templateProperties);
                        task = new WorkflowTaskInstance();
                        task.initialize(new BeanContext.Service(resolver), resource);
                        if (LOG.isInfoEnabled()) {
                            LOG.info("addTask({}): {}", task.getPath(), task.getTopic());
                        }
                    }
                } else {
                    if (LOG.isInfoEnabled()) {
                        LOG.info("nextTask({}): {}", task.getPath(), task.getTopic());
                    }
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        } else {
            LOG.error("task template not available: '{}'", nextTaskTemplate);
        }
    }

    /**
     * creates a job for execution of the a task instance (triggered by a user or another job)
     *
     * @param resolver     the user/job session
     * @param taskInstance the path to the task instance ('inbox' resource)
     * @param comment      an optional comment added to the task
     * @param data         the values for the task to execute (for the job; provided by the task dialog)
     */
    @Override
    public void runTask(@Nonnull ResourceResolver resolver,
                        @Nonnull String taskInstance, @Nullable Map<String, Object> data, @Nullable String comment) {
        WorkflowTaskInstance task = getInstance(resolver, taskInstance);
        if (task != null) {
            Map<String, Object> jobProperties = new HashMap<>();
            jobProperties.put(PN_TASK_INSTANCE_PATH, task.getPath());
            if (data != null && !data.isEmpty()) {
                jobProperties.put(PN_TASK_DATA, GSON.toJson(data));
            }
            if (StringUtils.isNotBlank(comment)) {
                jobProperties.put(PN_TASK_COMMENT, comment);
            }
            jobManager.addJob(task.getTopic(), jobProperties);
        } else {
            LOG.error("task instance not available: '{}'", taskInstance);
        }
    }

    /**
     * removes a task from the 'inbox'
     *
     * @param resolver     the user/job session
     * @param instancePath the path to the 'inbox' entry
     */
    public void removeTask(@Nonnull final ResourceResolver resolver,
                           @Nonnull final String instancePath) {
        try (ResourceResolver serviceResolver = resolverFactory.getServiceResourceResolver(null)) {
            Resource taskFile = serviceResolver.getResource(instancePath);
            if (taskFile != null) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("removeTask(): {}", instancePath);
                }
                serviceResolver.delete(taskFile);
            } else {
                LOG.error("removeTask({}) - task not available!", instancePath);
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    protected Resource getInstanceFolder(@Nonnull final ResourceResolver resolver, @Nullable final String tenantId) {
        String path = config.workflow_root()
                + "/" + (StringUtils.isNotBlank(tenantId) ? tenantId : config.shared_path())
                + "/" + config.inbox_path();
        Resource folder = resolver.getResource(path);
        if (folder == null) {
            LOG.error("workflow task folder must be exist ({})!", path);
        }
        return folder;
    }
}
