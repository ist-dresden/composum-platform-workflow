package com.composum.platform.workflow.impl;

import com.composum.platform.workflow.api.GenericProperties;
import com.composum.platform.workflow.api.WorkflowTask;
import com.composum.platform.workflow.api.WorkflowService;
import com.google.gson.stream.JsonReader;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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

    public static final String PN_TASK = "wf.task";

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
     * @param path     the repository path ('inbox' or an initial path from an app)
     */
    @Override
    @Nullable
    public WorkflowTask getTask(@Nonnull final ResourceResolver resolver, @Nonnull final String path) {
        WorkflowTask task = null;
        Resource template = resolver.getResource(path);
        if (template != null) {
            ValueMap values = template.getValueMap();
            if (JcrConstants.NT_FILE.equals(values.get(JcrConstants.JCR_PRIMARYTYPE, ""))) {
                template = template.getChild(JcrConstants.JCR_CONTENT);
                values = template != null ? template.getValueMap() : null;
            }
            if (values != null) {
                try (InputStream script = values.get(JcrConstants.JCR_DATA, InputStream.class)) {
                    if (script != null) {
                        try (InputStreamReader stream = new InputStreamReader(script, StandardCharsets.UTF_8);
                             JsonReader reader = new JsonReader(stream)) {
                            task = new WorkflowTask(new GenericProperties(reader));
                        }
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
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
    public WorkflowTask getTask(@Nonnull final ResourceResolver resolver, @Nonnull final Job job) {
        WorkflowTask task = null;
        String json = (String) job.getProperty(PN_TASK);
        if (StringUtils.isNotBlank(json)) {
            task = new WorkflowTask(new GenericProperties(json));
        }
        return task;
    }

    /**
     * builds a new (the next) task (for the 'inbox')
     *
     * @param resolver     the user session
     * @param previousTask the task of the job which has triggered the new task (optional)
     * @param nextTask     the template of the new task
     */
    @Override
    public void addTask(@Nonnull final ResourceResolver resolver, @Nullable final String tenantId,
                        @Nullable final WorkflowTask previousTask, @Nonnull final WorkflowTask nextTask) {
        Map<String, Object> properties = nextTask.getProperties();
        if (previousTask != null) {
            // merge properties of the previous task...
            previousTask.getProperties();
        }
        // store the task in the 'inbox'...
        Resource folder = getInstanceFolder(resolver, tenantId);
        if (folder != null) {
            try (ResourceResolver serviceResolver = resolverFactory.getServiceResourceResolver(null)) {
                Resource taskFile = serviceResolver.create(folder, UUID.randomUUID().toString(), properties);
                serviceResolver.create(taskFile, JcrConstants.JCR_CONTENT, properties);
                if (LOG.isInfoEnabled()) {
                    LOG.info("addTask(): {}", taskFile.getPath());
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    /**
     * creates a job for execution of the a task template (triggered by a user or another job)
     *
     * @param resolver     the user/job session
     * @param instancePath the path to the template ('inbox' entry)
     * @param comment      an optional comment added to the task
     */
    @Override
    public void runTask(@Nonnull final ResourceResolver resolver,
                        @Nonnull final String instancePath, @Nullable final String comment) {
        WorkflowTask task = getTask(resolver, instancePath);
        if (task != null) {
            runTask(resolver, task, comment);
            // remove task if it is an instance of the store
            removeTask(resolver, instancePath);
        } else {
            LOG.error("workflow instance not resolvable: '{}'", instancePath);
        }
    }

    /**
     * creates a job for execution of the a task template (triggered by a user or another job)
     *
     * @param resolver     the user/job session
     * @param taskTemplate the template to execute
     * @param comment      an optional comment added to the task
     */
    @Override
    public void runTask(@Nonnull final ResourceResolver resolver,
                        @Nonnull final WorkflowTask taskTemplate, @Nullable final String comment) {
        Map<String, Object> jobProperties = new HashMap<>();
        jobProperties.put(PN_TASK, taskTemplate.getProperties().toString());
        // add comment...
        jobManager.addJob(taskTemplate.getTopic(), jobProperties);
    }

    /**
     * removes a task from the 'inbox'
     *
     * @param resolver     the user/job session
     * @param instancePath the path to the 'inbox' entry
     */
    public void removeTask(@Nonnull final ResourceResolver resolver,
                           @Nonnull final String instancePath) {
        if (instancePath.startsWith(config.workflow_root())) {
            // remove task if it is an instance of the store only
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
