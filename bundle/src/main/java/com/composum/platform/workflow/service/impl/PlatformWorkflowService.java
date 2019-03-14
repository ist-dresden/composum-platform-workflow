package com.composum.platform.workflow.service.impl;

import com.composum.platform.models.simple.MetaData;
import com.composum.platform.workflow.model.Workflow;
import com.composum.platform.workflow.model.WorkflowTask;
import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import com.composum.sling.core.util.ResourceUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
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
import javax.jcr.query.Query;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.composum.platform.workflow.model.WorkflowTask.PP_COMMENTS;
import static com.composum.platform.workflow.model.WorkflowTask.PP_DATA;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.PN_CHOSEN_OPTION;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.PN_INITIATOR;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.PN_NEXT;

@Component(
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Platform Workflow Service"
        },
        immediate = true
)
@Designate(ocd = PlatformWorkflowService.Configuration.class)
public class PlatformWorkflowService implements WorkflowService {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformWorkflowService.class);

    protected static final Map<String, Object> STATE_FOLDER_PROPERTIES;

    static {
        STATE_FOLDER_PROPERTIES = new HashMap<>();
        STATE_FOLDER_PROPERTIES.put(JcrConstants.JCR_PRIMARYTYPE, ResourceUtil.TYPE_SLING_FOLDER);
    }

    protected static final Map<String, Object> SUBNODE_PROPERTIES;

    static {
        SUBNODE_PROPERTIES = new HashMap<>();
        SUBNODE_PROPERTIES.put(JcrConstants.JCR_PRIMARYTYPE, ResourceUtil.TYPE_UNSTRUCTURED);
    }

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
     * @param context  the current request context
     * @param tenantId the related tenant (must be selected by the user)
     */
    @Nonnull
    public Iterator<WorkflowTaskInstance> findTasks(@Nonnull final BeanContext context,
                                                    @Nullable final String tenantId) {
        ArrayList<WorkflowTaskInstance> tasks = new ArrayList<>();
        Resource folder = getInstanceFolder(context, tenantId, WorkflowTaskInstance.State.pending);
        if (folder != null) {
            for (Resource entry : folder.getChildren()) {
                if (true /* TODO: check assignee */) {
                    WorkflowTaskInstance task = loadTask(context, entry.getPath(), WorkflowTaskInstance.class);
                    tasks.add(task);
                }
            }
        }
        tasks.sort(Comparator.comparing(WorkflowTask::getDate));
        return tasks.iterator();
    }

    @Override
    @Nullable
    public Workflow getWorkflow(@Nonnull final BeanContext context, @Nonnull final Resource resource) {
        Workflow workflow = new Workflow(this);
        workflow.initialize(context, resource);
        return workflow.isEmpty() ? null : workflow;
    }

    @Override
    @Nonnull
    public Collection<Workflow> findInitiatedOpenWorkflows(@Nonnull final BeanContext context,
                                                           @Nonnull final String userId) {
        Collection<Workflow> workflows = findInitiatedWorkflows(context, userId);
        ArrayList<Workflow> openWorkflows = new ArrayList<>();
        for (Workflow workflow : workflows) {
            if (workflow.isOpen()) {
                openWorkflows.add(workflow);
            }
        }
        return openWorkflows;
    }

    @Override
    @Nonnull
    public Collection<Workflow> findInitiatedWorkflows(@Nonnull final BeanContext context,
                                                       @Nonnull final String userId) {
        ArrayList<Workflow> workflows = new ArrayList<>();
        try (final ServiceContext serviceContext = new ServiceContext(context,
                resolverFactory.getServiceResourceResolver(null))) {
            String query = "/jcr:root" + config.workflow_root() + "/*/*/*[@" + PN_INITIATOR + "='" + userId + "']";
            @SuppressWarnings("deprecation")
            Iterator<Resource> foundTasks = serviceContext.getResolver().findResources(query, Query.XPATH);
            while (foundTasks.hasNext()) {
                WorkflowTaskInstance task = loadInstance(serviceContext, foundTasks.next().getPath());
                if (task != null) {
                    Workflow workflow = null;
                    for (Workflow wf : workflows) {
                        if (wf.containsTasks(task)) {
                            workflow = wf;
                            break;
                        }
                    }
                    if (workflow == null) {
                        workflow = getWorkflow(serviceContext, task.getResource());
                        workflows.add(workflow);
                    }
                }
            }
        } catch (LoginException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return workflows;
    }

    /**
     * loads a task instance from the repository
     *
     * @param context  the current request context
     * @param pathOrId the repository path or the id of the task
     */
    @Override
    @Nullable
    public WorkflowTaskInstance getInstance(@Nullable final BeanContext context, @Nonnull final String pathOrId) {
        if (pathOrId.startsWith(config.workflow_root()) || !pathOrId.contains("/")) {
            try (final ServiceContext serviceContext = new ServiceContext(context,
                    resolverFactory.getServiceResourceResolver(null))) {
                return loadInstance(serviceContext, pathOrId);
            } catch (LoginException ex) {
                LOG.error(ex.toString());
            }
        }
        return null;
    }

    /**
     * loads a task template from the repository
     *
     * @param context the current request context
     * @param path    the repository path
     */
    @Override
    @Nullable
    public WorkflowTaskTemplate getTemplate(@Nullable final BeanContext context, @Nonnull final String path) {
        if (!path.startsWith(config.workflow_root()) && path.contains("/")) {
            try (final ServiceContext serviceContext = new ServiceContext(context,
                    resolverFactory.getServiceResourceResolver(null))) {
                return loadTemplate(serviceContext, path);
            } catch (LoginException ex) {
                LOG.error(ex.toString());
            }
        }
        return null;
    }

    /**
     * restores a task for the properties of a job
     *
     * @param context the current request context
     * @param job     the job instance
     */
    @Override
    @Nullable
    public WorkflowTaskInstance getInstance(@Nonnull final BeanContext context, @Nonnull final Job job) {
        WorkflowTaskInstance task = null;
        String path = (String) job.getProperty(PN_TASK_INSTANCE_PATH);
        if (StringUtils.isNotBlank(path)) {
            try (final ServiceContext serviceContext = new ServiceContext(context,
                    resolverFactory.getServiceResourceResolver(null))) {
                task = loadInstance(serviceContext, path);
            } catch (LoginException ex) {
                LOG.error(ex.toString());
            }

        }
        return task;
    }

    /**
     * @return the current state of the task instance
     */
    @Override
    @Nullable
    public WorkflowTaskInstance.State getState(@Nonnull final WorkflowTaskInstance instance) {
        Matcher matcher = getPathMatcher(instance.getResource());
        return matcher.matches() & StringUtils.isNotBlank(matcher.group(3))
                ? WorkflowTaskInstance.State.valueOf(matcher.group(3)) : null;
    }

    /**
     * builds a new (the next) task (for the 'inbox')
     *
     * @param context      the current request context
     * @param tenantId     the related tenant (selected by the user or inherited from the previous task)
     * @param previousTask the path of the previous instance which has triggered the new task (optional)
     * @param taskTemplate the path of the template of the new task
     * @param comment      an optional comment added to the task
     * @param data         the properties for the task ('data' must be named as 'data/key')
     * @param jobMetaData  the task meta data from the calling job
     * @return the model of the created instance
     */
    @Override
    @Nullable
    public WorkflowTaskInstance addTask(@Nullable final BeanContext context, @Nullable String tenantId,
                                        @Nullable final String previousTask, @Nonnull final String taskTemplate,
                                        @Nullable final String comment, @Nullable final Map<String, Object> data,
                                        @Nullable final MetaData jobMetaData) {
        WorkflowTaskInstance taskInstance = null;
        try (final ServiceContext serviceContext = new ServiceContext(context,
                resolverFactory.getServiceResourceResolver(null))) {
            final WorkflowTaskTemplate template =
                    loadTask(serviceContext, taskTemplate, WorkflowTaskTemplate.class);
            if (template != null) {
                final WorkflowTaskInstance previous = previousTask != null
                        ? loadTask(serviceContext, previousTask, WorkflowTaskInstance.class) : null;
                final MetaData metaData = getMetaData(jobMetaData, serviceContext, tenantId, previous);
                String assignee;
                Map<String, Object> properties = new HashMap<>();
                properties.put(WorkflowTaskInstance.PN_TOPIC, template.getTopic());
                properties.put(WorkflowTaskInstance.PN_CATEGORY, template.getCategory());
                properties.put(WorkflowTaskInstance.PN_ASSIGNEE, assignee = metaData.getValue(template.getAssignee()));
                properties.put(WorkflowTaskInstance.PN_INITIATOR, metaData.get(META_USER_ID));
                properties.put(WorkflowTaskInstance.PN_TEMPLATE, template.getPath());
                if (previous != null) {
                    properties.put(WorkflowTaskInstance.PN_PREVIOUS, previous.getName());
                }
                // store the task in the 'inbox'...
                final Resource folder = giveInstanceFolder(serviceContext, tenantId != null
                                ? tenantId : (previous != null ? getTenantId(previous.getResource()) : null),
                        WorkflowTaskInstance.State.pending);
                String name = "task-" + UUID.randomUUID().toString();
                String path = folder.getPath() + "/" + name;
                Resource taskResource = serviceContext.getResolver().create(folder, name, properties);
                Resource taskData = serviceContext.getResolver().create(taskResource, PP_DATA, SUBNODE_PROPERTIES);
                taskInstance = loadTask(serviceContext, path, WorkflowTaskInstance.class);
                if (LOG.isInfoEnabled()) {
                    LOG.info("addTask({}): {}", template.getPath(), taskInstance);
                }
                if (taskInstance != null) {
                    changeTaskData(taskInstance, PP_DATA, serviceContext, template.getData(), metaData);
                    if (previous != null) {
                        changeTaskData(taskInstance, PP_DATA, serviceContext, previous.getData(), metaData);
                        changeTaskData(previous, null, serviceContext,
                                Collections.singletonMap(PN_NEXT, previous.getName()), metaData);
                    }
                    if (data != null) {
                        changeTaskData(taskInstance, PP_DATA, serviceContext, data, metaData);
                    }
                }
                if (StringUtils.isBlank(assignee)) {
                    // auto run task if no assignee is declared
                    taskInstance = runTask(serviceContext, path, null, null, null, metaData);
                }
                serviceContext.getResolver().commit();
            } else {
                LOG.error("task template not available: '{}'", taskTemplate);
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return taskInstance;
    }

    /**
     * creates a job for execution of the a task instance (triggered by a user or another job)
     *
     * @param context          the current request context
     * @param taskInstancePath the path to the task instance ('inbox' resource)
     * @param option           the users choice for the next workflow step
     * @param comment          an optional comment added to the task
     * @param data             the values for the task to execute ('data' must be named as 'data/key')
     */
    @Override
    @Nullable
    public WorkflowTaskInstance runTask(@Nullable final BeanContext context,
                                        @Nonnull final String taskInstancePath, @Nullable final String option,
                                        @Nullable final String comment, @Nullable final Map<String, Object> data,
                                        @Nullable final MetaData jobMetaData)
            throws PersistenceException {
        WorkflowTaskInstance taskInstance = null;
        try (final ServiceContext serviceContext = new ServiceContext(context,
                resolverFactory.getServiceResourceResolver(null))) {
            taskInstance = loadInstance(serviceContext, taskInstancePath);
            if (taskInstance != null) {
                final MetaData metaData = getMetaData(jobMetaData, serviceContext, null, taskInstance);
                Resource runningFolder = giveInstanceFolder(serviceContext, getTenantId(taskInstance.getResource()),
                        WorkflowTaskInstance.State.running);
                Resource moved = serviceContext.getResolver().move(taskInstancePath, runningFolder.getPath());
                taskInstance = loadTask(serviceContext, moved, WorkflowTaskInstance.class);
                if (taskInstance != null) {
                    addTaskComment(taskInstance, serviceContext, comment);
                    changeTaskData(taskInstance, PP_DATA, serviceContext, data, metaData);
                    Map<String, Object> jobProperties = new HashMap<>();
                    jobProperties.put(PN_TASK_INSTANCE_PATH, taskInstance.getPath());
                    if (StringUtils.isNotBlank(option)) {
                        jobProperties.put(PN_TASK_OPTION, option);
                        changeTaskData(taskInstance, null, serviceContext,
                                Collections.singletonMap(PN_CHOSEN_OPTION, option), null);
                    }
                    jobProperties.put(PN_TASK_INITIATOR, metaData.get("userId"));
                    jobManager.addJob(taskInstance.getTopic(), jobProperties);
                    serviceContext.getResolver().commit();
                } else {
                    LOG.error("task instance can't be moved to state folder: '{}'", moved);
                }
            } else {
                LOG.error("task instance not available: '{}'", taskInstancePath);
            }
        } catch (LoginException ex) {
            LOG.error(ex.toString());
        }
        return taskInstance;
    }

    /**
     * finishes the execution of the given task
     *
     * @param context          the current request context
     * @param taskInstancePath the path to the task instance ('inbox' resource)
     * @param cancelled        should be 'true' if the workflow execution is cancelled
     * @param comment          an optional comment added to the task
     * @param data             the values for the task to execute ('data' must be named as 'data/key')
     */
    @Override
    @Nullable
    public WorkflowTaskInstance finishTask(@Nullable final BeanContext context,
                                           @Nonnull final String taskInstancePath, boolean cancelled,
                                           @Nullable final String comment, @Nullable final Map<String, Object> data,
                                           @Nullable final MetaData jobMetaData)
            throws PersistenceException {
        WorkflowTaskInstance taskInstance = null;
        try (final ServiceContext serviceContext = new ServiceContext(context,
                resolverFactory.getServiceResourceResolver(null))) {
            taskInstance = loadInstance(serviceContext, taskInstancePath);
            if (taskInstance != null) {
                final MetaData metaData = getMetaData(jobMetaData, serviceContext, null, taskInstance);
                Resource runningFolder = giveInstanceFolder(serviceContext, getTenantId(taskInstance.getResource()),
                        WorkflowTaskInstance.State.finished);
                Resource moved = serviceContext.getResolver().move(taskInstancePath, runningFolder.getPath());
                taskInstance = loadTask(serviceContext, moved, WorkflowTaskInstance.class);
                if (taskInstance != null) {
                    addTaskComment(taskInstance, serviceContext, comment);
                    changeTaskData(taskInstance, PP_DATA, serviceContext, data, metaData);
                    Map<String, Object> opData = new HashMap<>();
                    opData.put(cancelled ? "cancelled" : "finished", Calendar.getInstance());
                    if (context != null) {
                        opData.put((cancelled ? "cancelled" : "finished") + "By", context.getResolver().getUserID());
                    }
                    changeTaskData(taskInstance, null, serviceContext, opData, metaData);
                    serviceContext.getResolver().commit();
                } else {
                    LOG.error("task instance can't be moved to state folder: '{}'", moved);
                }
            } else {
                LOG.error("task instance not available: '{}'", taskInstancePath);
            }
        } catch (LoginException ex) {
            LOG.error(ex.toString());
        }
        return taskInstance;
    }

    /**
     * removes a task from the task store
     *
     * @param context      the current request context
     * @param instancePath the path to the task instance
     */
    @Override
    public void removeTask(@Nonnull final BeanContext context, @Nonnull final String instancePath)
            throws PersistenceException {
        try (ResourceResolver serviceResolver = resolverFactory.getServiceResourceResolver(null)) {
            Resource taskFile = serviceResolver.getResource(instancePath);
            if (taskFile != null) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("removeTask(): {}", instancePath);
                }
                serviceResolver.delete(taskFile);
                serviceResolver.commit();
            } else {
                LOG.error("removeTask({}) - task not available!", instancePath);
            }
        } catch (LoginException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    /**
     * add a comment if comment is not empty
     *
     * @param taskInstance the task instance to change
     * @param context      the current request context
     * @param comment      the comment text
     */
    protected void addTaskComment(@Nonnull final WorkflowTaskInstance taskInstance,
                                  @Nonnull final ServiceContext context, @Nullable final String comment)
            throws PersistenceException {
        if (StringUtils.isNotBlank(comment)) {
            ResourceResolver resolver = context.getResolver();
            Resource taskResource = taskInstance.getResource();
            Resource comments = taskResource.getChild(PP_COMMENTS);
            if (comments == null) {
                comments = resolver.create(taskResource, PP_COMMENTS, SUBNODE_PROPERTIES);
            }
            Map<String, Object> commentValues = new HashMap<>();
            commentValues.put("date", Calendar.getInstance());
            commentValues.put("text", comment);
            String userId = context.getUserId();
            if (StringUtils.isNotBlank(userId)) {
                commentValues.put("user", userId);
            }
            resolver.create(comments, "comment-" + UUID.randomUUID().toString(), commentValues);
            resolver.commit();
        }
    }

    /**
     * chamges the properties of the task instanmce
     *
     * @param taskInstance the task instance to change
     * @param context      the current request context
     * @param data         the properties to change / to remove (removed if value is 'null')
     * @param meta         the current operation meta data (tenant, user, ...)
     */
    protected void changeTaskData(@Nonnull final WorkflowTaskInstance taskInstance, @Nullable final String subPath,
                                  @Nullable final ServiceContext context, @Nullable final Map<String, Object> data,
                                  @Nullable final MetaData meta)
            throws PersistenceException {
        Resource resource = taskInstance.getResource();
        if (StringUtils.isNotBlank(subPath)) {
            resource = resource.getChild(subPath);
        }
        if (resource != null) {
            ModifiableValueMap values = resource.adaptTo(ModifiableValueMap.class);
            if (values != null) {
                changeProperties(values, data, meta);
            } else {
                throw new PersistenceException("can't modify properties of '" + taskInstance.getResource().getPath() + "'");
            }
        } else {
            throw new PersistenceException("no child '" + subPath + "' available");
        }
    }

    protected void changeProperties(@Nonnull final Map<String, Object> values,
                                    @Nullable final Map<String, Object> data,
                                    @Nullable final MetaData meta) {
        if (data != null) {
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                Object value = entry.getValue();
                if (value != null) {
                    if (meta != null && value instanceof String) {
                        value = meta.getValue((String) value);
                    }
                    values.put(entry.getKey(), value);
                } else {
                    values.remove(entry.getKey());
                }
            }
        }
    }

    //

    /**
     * a context with a service resolver overlay and access to the requesting resolver (user id / session)
     */
    protected class ServiceContext extends BeanContext.Wrapper implements Closeable {

        protected final String userId;

        protected ServiceContext(BeanContext requestContext, ResourceResolver serviceResolver) {
            super(requestContext, serviceResolver);
            if (beanContext == null) {
                beanContext = new BeanContext.Service(serviceResolver);
                userId = null;
            } else {
                userId = beanContext.getResolver().getUserID();
            }
        }

        public String getUserId() {
            return userId;
        }

        @Override
        public void close() {
            resolver.close();
        }
    }

    protected MetaData getMetaData(@Nullable MetaData metaData, @Nonnull final ServiceContext context,
                                   @Nullable final String givenTenantId, @Nullable WorkflowTaskInstance task) {
        if (metaData == null) {
            metaData = new MetaData();
        }
        metaData.putIfAbsent(META_TENANT_ID, givenTenantId != null ? givenTenantId
                : (task != null ? getTenantId(task.getResource()) : "platform"));
        final String userId = context.getUserId();
        metaData.putIfAbsent(META_USER_ID, userId != null ? userId : "service");
        return metaData;
    }

    @Nullable
    protected WorkflowTaskInstance loadInstance(@Nonnull final ServiceContext serviceContext,
                                                @Nonnull final String pathOrId) {
        return loadTask(serviceContext, pathOrId, WorkflowTaskInstance.class);
    }

    @Nullable
    protected WorkflowTaskTemplate loadTemplate(@Nonnull final ServiceContext serviceContext,
                                                @Nonnull final String path) {
        return loadTask(serviceContext, path, WorkflowTaskTemplate.class);
    }

    @Nullable
    protected <T extends WorkflowTask> T loadTask(@Nonnull final BeanContext context,
                                                  @Nonnull final String pathOrId,
                                                  @Nonnull Class<T> type) {
        Resource resource = getTaskResource(context, pathOrId);
        if (resource != null) {
            return loadTask(context, resource, type);
        }

        return null;
    }

    @Nullable
    protected <T extends WorkflowTask> T loadTask(@Nonnull final BeanContext context,
                                                  @Nonnull final Resource resource,
                                                  @Nonnull Class<T> type) {
        T task;
        try {
            task = type.getConstructor(WorkflowService.class).newInstance(this);
            task.initialize(context, resource);
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
            task = null;
        }
        return task;
    }

    @Nonnull
    protected Resource giveInstanceFolder(@Nonnull final ServiceContext context, @Nullable final String tenantId,
                                          @Nonnull final WorkflowTaskInstance.State state)
            throws PersistenceException {
        Resource stateFolder;
        Resource folder = getInstanceFolder(context, tenantId);
        if (folder != null) {
            stateFolder = folder.getChild(state.name());
            if (stateFolder == null) {
                ResourceResolver resolver = context.getResolver();
                stateFolder = resolver.create(folder, state.name(), STATE_FOLDER_PROPERTIES);
            }
        } else {
            throw new PersistenceException("tenant folder must exist for tenant '" + tenantId + "'");
        }
        return stateFolder;
    }

    @Nullable
    protected Resource getInstanceFolder(@Nonnull final BeanContext context, @Nullable final String tenantId,
                                         @Nonnull final WorkflowTaskInstance.State state) {
        Resource folder = getInstanceFolder(context, tenantId);
        if (folder != null) {
            return folder.getChild(state.name());
        }
        return null;
    }

    @Nullable
    protected Resource getInstanceFolder(@Nonnull final BeanContext context, @Nullable final String tenantId) {
        String path = config.workflow_root()
                + "/" + (StringUtils.isNotBlank(tenantId) ? tenantId : config.general_path());
        Resource folder = context.getResolver().getResource(path);
        if (folder == null) {
            LOG.error("tenant workflow task folder must be exist ({})!", path);
        }
        return folder;
    }

    /**
     * @return the tenant id from the task resource path
     */
    @Override
    public String getTenantId(Resource taskResource) {
        Matcher matcher = getPathMatcher(taskResource);
        if (matcher.matches()) {
            String tenantId = matcher.group(1);
            if (!tenantId.equals(config.general_path())) {
                return tenantId;
            }
        }
        return null;
    }

    /**
     * @return the path segments matcher: [1]=tenant, [3]=state, [4]=id; doesn't match if not a valid instance
     */
    protected Matcher getPathMatcher(Resource taskResource) {
        return getPathMatcher(taskResource.getPath());
    }

    /**
     * @return the path segments matcher: [1]=tenant, [3]=state, [4]=id; doesn't match if not a valid instance
     */
    protected Matcher getPathMatcher(String path) {
        Pattern pathPattern = Pattern.compile("^" + config.workflow_root() + "/([^/]+)(/([^/]+)/([^/]+))?$");
        return pathPattern.matcher(path);
    }

    protected Resource getTaskResource(BeanContext context, String pathOrId) {
        Resource resource = null;
        ResourceResolver resolver = context.getResolver();
        if (!pathOrId.contains("/")) { // use task id for a query
            String query = "/jcr:root" + config.workflow_root() + "//" + pathOrId;
            //noinspection deprecation
            Iterator<Resource> found = resolver.findResources(query, Query.XPATH);
            if (found.hasNext()) {
                resource = found.next();
            }
        } else { // use task path for retrieval
            resource = resolver.getResource(pathOrId);
            if (resource == null) {
                Matcher matcher = getPathMatcher(pathOrId);
                if (matcher.matches() && StringUtils.isNotBlank(matcher.group(4))) {
                    for (WorkflowTaskInstance.State state : WorkflowTaskInstance.State.values()) {
                        if ((resource = resolver.getResource(config.workflow_root()
                                + "/" + matcher.group(1) + "/" + state.name() + "/" + matcher.group(4))) != null) {
                            break;
                        }
                    }
                }
            }
        }
        return resource;
    }
}
