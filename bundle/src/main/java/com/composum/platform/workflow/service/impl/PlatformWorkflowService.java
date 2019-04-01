package com.composum.platform.workflow.service.impl;

import com.composum.platform.models.simple.MetaData;
import com.composum.platform.workflow.WorkflowAction;
import com.composum.platform.workflow.model.Workflow;
import com.composum.platform.workflow.model.WorkflowTask;
import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.platform.workflow.service.WorkflowActionManager;
import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import com.composum.sling.core.SlingBean;
import com.composum.sling.core.filter.ResourceFilter;
import com.composum.sling.core.service.PermissionsService;
import com.composum.sling.core.util.ResourceUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.wrappers.ValueMapDecorator;
import org.apache.sling.tenant.Tenant;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.Designate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jcr.Session;
import javax.jcr.query.Query;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.composum.platform.workflow.model.Workflow.RA_WORKFLOW;
import static com.composum.platform.workflow.model.Workflow.WORKFLOW_NODE;
import static com.composum.platform.workflow.model.Workflow.WORKFLOW_TYPE;
import static com.composum.platform.workflow.model.WorkflowTask.PN_ASSIGNEE;
import static com.composum.platform.workflow.model.WorkflowTask.PP_COMMENTS;
import static com.composum.platform.workflow.model.WorkflowTask.PP_DATA;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.INSTANCE_TYPE;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.PN_CANCELLED;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.PN_CANCELLED_BY;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.PN_CHOSEN_OPTION;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.PN_EXECUTED;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.PN_EXECUTED_BY;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.PN_FINISHED;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.PN_FINISHED_BY;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.PN_INITIATOR;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.PN_NEXT;
import static com.composum.platform.workflow.model.WorkflowTaskInstance.PN_TEMPLATE;
import static com.composum.platform.workflow.model.WorkflowTaskTemplate.TEMPLATE_TYPE;

@Component(
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Platform Workflow Service"
        },
        immediate = true
)
@Designate(ocd = WorkflowService.Configuration.class)
public class PlatformWorkflowService implements WorkflowService {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformWorkflowService.class);

    protected static final Map<String, Object> STATE_FOLDER_PROPERTIES;

    static {
        STATE_FOLDER_PROPERTIES = new HashMap<>();
        STATE_FOLDER_PROPERTIES.put(JcrConstants.JCR_PRIMARYTYPE, ResourceUtil.TYPE_SLING_ORDERED_FOLDER);
    }

    protected static final Map<String, Object> SUBNODE_PROPERTIES;

    static {
        SUBNODE_PROPERTIES = new HashMap<>();
        SUBNODE_PROPERTIES.put(JcrConstants.JCR_PRIMARYTYPE, ResourceUtil.TYPE_UNSTRUCTURED);
    }

    protected static final String WRITE_PRIVILEGE_KEY = "rep:write";
    protected static final String[] TASK_PRIVILEGE_KEYS = new String[]{"jcr:read"};

    protected class TaskInstanceAssigneeFilter implements ResourceFilter {

        @Override
        public boolean accept(Resource resource) {
            if (!resource.isResourceType(INSTANCE_TYPE)) {
                return false;
            }
            ValueMap values = resource.getValueMap();
            String assignee = values.get(PN_ASSIGNEE, "");
            Session session = resource.getResourceResolver().adaptTo(Session.class);
            return StringUtils.isBlank(assignee) || (session != null &&
                    (permissionsService.isMemberOfAll(session, assignee) || assignee.equals(session.getUserID())));
        }

        @Override
        public boolean isRestriction() {
            return true;
        }

        @Override
        public void toString(StringBuilder builder) {
            builder.append("TaskAssignee");
        }
    }

    @Reference
    protected ResourceResolverFactory resolverFactory;

    @Reference
    protected PermissionsService permissionsService;

    @Reference
    protected WorkflowActionManager actionManager;

    protected Configuration config;

    @Activate
    @Modified
    protected void activate(BundleContext bundleContext, Configuration config) {
        this.config = config;
    }

    @Override
    @Nullable
    public Configuration getConfig() {
        return config;
    }

    /**
     * retrieves the list of available workflows
     *
     * @param context  the current request context
     * @param tenantId the related tenant (must be selected by the user)
     * @param target   the resource context to retrieve appropriate workflows
     */
    @Override
    @Nonnull
    public Iterator<Workflow> findWorkflows(@Nonnull final BeanContext context,
                                            @Nullable final String tenantId,
                                            @Nullable final Resource target) {
        ArrayList<Workflow> workflows = new ArrayList<>();
        ResourceResolver resolver = context.getResolver();
        String query = "/jcr:root/conf//" + WORKFLOW_NODE + "[@sling:resourceType='" + WORKFLOW_TYPE + "']";
        @SuppressWarnings("deprecation")
        Iterator<Resource> found = resolver.findResources(query, Query.XPATH);
        ResourceFilter resourceFilter = ResourceFilter.ALL; // TODO... new TaskInstanceAssigneeFilter();
        while (found.hasNext()) {
            Resource workflowResource = found.next();
            if (resourceFilter.accept(workflowResource)) {
                Resource templateResource = workflowResource.getParent();
                if (templateResource != null && templateResource.isResourceType(TEMPLATE_TYPE)) {
                    Workflow workflow = loadWorkflow(context, templateResource.getPath());
                    if (workflow != null) {
                        ResourceFilter targetFilter = workflow.getTargetFilter();
                        if (targetFilter == null || (target != null && targetFilter.accept(target))) {
                            workflows.add(workflow);
                        }
                    }
                }
            }
        }
        workflows.sort(Comparator.comparing(Workflow::getTitle));
        return workflows.iterator();
    }

    @Override
    @Nullable
    public Workflow getWorkflow(@Nonnull final BeanContext context, @Nonnull final Resource resource) {
        Workflow workflow = loadWorkflow(context, resource.getPath());
        return workflow == null || workflow.isHollow() ? null : workflow;
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
        try (final ServiceContext serviceContext = new ServiceContext(context)) {
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
     * retrieves the list of tasks in the requested scope
     *
     * @param context  the current request context
     * @param tenantId the related tenant (must be selected by the user)
     * @param scope    the status scope of the retrieval; default: pending
     */
    @Override
    @Nonnull
    public Iterator<WorkflowTaskInstance> findTasks(@Nonnull final BeanContext context,
                                                    @Nullable final String tenantId,
                                                    @Nullable final WorkflowTaskInstance.State scope) {
        ArrayList<WorkflowTaskInstance> tasks = new ArrayList<>();
        ResourceFilter filter = new TaskInstanceAssigneeFilter();
        if (StringUtils.isNotBlank(tenantId)) {
            Resource folder = getInstanceFolder(context, tenantId, scope != null ? scope : WorkflowTaskInstance.State.pending);
            if (folder != null) {
                for (Resource taskRes : folder.getChildren()) {
                    if (filter.accept(taskRes)) {
                        WorkflowTaskInstance task = loadInstance(context, taskRes.getPath());
                        tasks.add(task);
                    }
                }
            }
        } else {
            ResourceResolver resolver = context.getResolver();
            String query = "/jcr:root" + config.workflow_root()
                    + "//" + (scope != null ? scope : WorkflowTaskInstance.State.pending) + "/*"
                    + "[@sling:resourceType='" + INSTANCE_TYPE + "']";
            @SuppressWarnings("deprecation")
            Iterator<Resource> found = resolver.findResources(query, Query.XPATH);
            while (found.hasNext()) {
                Resource taskRes = found.next();
                if (filter.accept(taskRes)) {
                    WorkflowTaskInstance task = loadInstance(context, taskRes.getPath());
                    tasks.add(task);
                }
            }
        }
        tasks.sort(Comparator.comparing(WorkflowTaskInstance::getTime));
        return tasks.iterator();
    }

    /**
     * loads a task instance from the repository
     *
     * @param context  the current request context
     * @param pathOrId the repository path or the id of the task
     */
    @Override
    @Nullable
    public WorkflowTaskInstance getInstance(@Nonnull final BeanContext context, @Nonnull final String pathOrId) {
        if (pathOrId.startsWith(config.workflow_root()) || !pathOrId.contains("/")) {
            return loadInstance(context, pathOrId);
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
    public WorkflowTaskTemplate getTemplate(@Nonnull final BeanContext context, @Nonnull final String path) {
        if (!path.startsWith(config.workflow_root()) && path.contains("/")) {
            return loadTemplate(context, path);
        }
        return null;
    }

    /**
     * @return the current state of the task instance
     */
    @Override
    @Nullable
    public WorkflowTaskInstance.State getState(@Nonnull final BeanContext context, @Nonnull final String pathOrId) {
        Resource taskResource = getTaskResource(context, pathOrId);
        if (taskResource != null) {
            return getState(taskResource);
        }
        return null;
    }

    @Nullable
    protected WorkflowTaskInstance.State getState(@Nonnull final Resource taskResource) {
        Matcher matcher = getPathMatcher(taskResource);
        return matcher.matches() && StringUtils.isNotBlank(matcher.group(3))
                ? WorkflowTaskInstance.State.valueOf(matcher.group(3)) : null;
    }

    /**
     * @return the tenant id derived from the hint (task instance path or another path or a tenant parameter)
     */
    @Override
    public String getTenantId(@Nonnull final BeanContext context, @Nullable final String hint) {
        if (StringUtils.isNotBlank(hint)) {
            Resource resource = context.getResolver().getResource(
                    hint.startsWith("/") ? hint : config.general_path() + "/" + hint);
            if (resource != null) {
                Tenant tenant = resource.adaptTo(Tenant.class);
                if (tenant != null) {
                    return tenant.getId();
                }
            }
            WorkflowTaskInstance instance = loadInstanceRef(context, hint);
            if (instance != null) {
                return getTenantId(instance.getResource());
            }
        }
        return null;
    }

    /**
     * @return the tenant id from the task resource path
     */
    protected String getTenantId(Resource taskResource) {
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
     * builds a new (the next) task (for the 'inbox')
     *
     * @param context        the current request context
     * @param tenantId       the related tenant (selected by the user or inherited from the previous task)
     * @param previousTask   the path of the previous instance which has triggered the new task (optional)
     * @param taskTemplate   the path of the template of the new task
     * @param comment        an optional comment added to the task
     * @param data           the properties for the task ('data' must be named as 'data/key')
     * @param actionMetaData the task meta data from the calling job
     * @return the model of the created instance
     */
    @Override
    @Nullable
    public WorkflowTaskInstance addTask(@Nonnull final BeanContext context, @Nullable String tenantId,
                                        @Nullable final String previousTask, @Nonnull final String taskTemplate,
                                        @Nonnull final List<String> target, @Nullable final Map<String, Object> data,
                                        @Nullable final String comment, @Nullable final MetaData actionMetaData) {
        WorkflowTaskInstance taskInstance = null;
        final WorkflowTaskTemplate template = loadTemplate(context, taskTemplate);
        if (template != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("addTask; '{}'...", taskTemplate);
            }
            try (final ServiceContext serviceContext = new ServiceContext(
                    context instanceof ServiceContext ? ((ServiceContext) context).getRequestContext() : context)) {
                final WorkflowTaskInstance previous = previousTask != null
                        ? loadInstance(serviceContext, previousTask) : null;
                final MetaData metaData = getMetaData(actionMetaData, serviceContext, tenantId, previous);
                String initiator = (String) metaData.get(META_USER_ID);
                String assignee = adjustAssignee(serviceContext, metaData.getValue(template.getAssignee()), Group.class);
                Map<String, Object> properties = new HashMap<>();
                properties.put(JcrConstants.JCR_PRIMARYTYPE, ResourceUtil.TYPE_SLING_FOLDER);
                properties.put(ResourceUtil.PROP_RESOURCE_TYPE, INSTANCE_TYPE);
                properties.put(WorkflowTaskInstance.PN_TEMPLATE, template.getPath());
                properties.put(WorkflowTaskInstance.PN_TARGET, target.toArray(new String[0]));
                if (StringUtils.isNotBlank(assignee)) {
                    properties.put(WorkflowTaskInstance.PN_ASSIGNEE, assignee);
                }
                if (StringUtils.isNotBlank(initiator)) {
                    properties.put(WorkflowTaskInstance.PN_INITIATOR, initiator);
                }
                if (previous != null) {
                    properties.put(WorkflowTaskInstance.PN_PREVIOUS, previous.getName());
                }
                // store the task in the 'inbox'...
                final Resource folder = giveInstanceFolder(serviceContext, tenantId != null
                                ? tenantId : (previous != null ? getTenantId(previous.getResource()) : null),
                        WorkflowTaskInstance.State.pending);
                String name = "wft-" + UUID.randomUUID().toString();
                String path = folder.getPath() + "/" + name;
                Resource taskResource = serviceContext.getResolver().create(folder, name, properties);
                Resource taskData = serviceContext.getResolver().create(taskResource, PP_DATA, SUBNODE_PROPERTIES);
                taskInstance = loadInstance(serviceContext, path);
                if (taskInstance != null) {
                    changeTaskData(serviceContext, taskInstance, PP_DATA, template.getData(), metaData);
                    if (previous != null) {
                        changeTaskData(serviceContext, taskInstance, PP_DATA, previous.getData(), metaData);
                        changeTaskData(serviceContext, previous, null,
                                Collections.singletonMap(PN_NEXT, taskInstance.getName()), metaData);
                    }
                    if (data != null) {
                        changeTaskData(serviceContext, taskInstance, PP_DATA, data, metaData);
                    }
                    /* FIXME - OAK exception on moving a node with ACLs:
                       javax.jcr.InvalidItemStateException: This item [/var/composum/workflow/.../running/wft-.../data] does not exist anymore
                    if (StringUtils.isNotBlank(assignee)) {
                        Session session = serviceContext.getResolver().adaptTo(Session.class);
                        if (session != null) {
                            AccessControlManager acManager = session.getAccessControlManager();
                            final PrincipalManager principalManager = ((JackrabbitSession) session).getPrincipalManager();
                            final JackrabbitAccessControlList policies = AccessControlUtils.getAccessControlList(acManager, path);
                            final Principal principal = principalManager.getPrincipal(assignee);
                            if (principal != null) {
                                final Privilege[] privileges = AccessControlUtils.privilegesFromNames(acManager, TASK_PRIVILEGE_KEYS);
                                policies.addEntry(principal, privileges, true, Collections.emptyMap());
                                acManager.setPolicy(path, policies);
                            } else {
                                throw new IllegalArgumentException("assignee '" + assignee + "' is not known");
                            }
                        } else {
                            throw new IllegalStateException("can't adapt resolver to session");
                        }
                    }
                    */
                    serviceContext.getResolver().commit();
                    if (LOG.isInfoEnabled()) {
                        LOG.info("addTask({}) done: '{}'{}", template, taskInstance,
                                template.isAutoRun() ? " -> autoRun..." : "");
                    }
                    if (template.isAutoRun()) {
                        taskInstance = runTask(serviceContext, taskInstance, null, null, null, metaData, true);
                    }
                } else {
                    LOG.error("created task not available ({})", path);
                }
            } catch (IllegalArgumentException ex) {
                LOG.error(ex.toString());
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        } else {
            LOG.error("task template not available: '{}'", taskTemplate);
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
    public WorkflowTaskInstance runTask(@Nonnull final BeanContext context,
                                        @Nonnull final String taskInstancePath, @Nullable final String option,
                                        @Nullable final String comment, @Nullable final Map<String, Object> data,
                                        @Nullable final MetaData actionMetaData)
            throws PersistenceException {
        WorkflowTaskInstance result = null;
        WorkflowTaskInstance task = loadInstance(context, taskInstancePath);
        if (task != null) {
            if (task.getState() == WorkflowTaskInstance.State.pending) {
                if (new TaskInstanceAssigneeFilter().accept(task.getResource())) {
                    try (final ServiceContext serviceContext = new ServiceContext(context)) {
                        result = runTask(serviceContext, task, option, comment, data, actionMetaData, true);
                    } catch (LoginException ex) {
                        LOG.error(ex.toString());
                    }
                } else {
                    LOG.error("insufficient privileges: '{}' ({})", context.getResolver().getUserID(), taskInstancePath);
                }
            } else {
                LOG.error("can't run task in state: '{}' ({})", task.getState(), taskInstancePath);
                throw new PersistenceException("can't run task, task not pending");
            }
        } else {
            LOG.error("task not available: '{}'", taskInstancePath);
        }
        return result;
    }

    /**
     * the internal 'run' using a given service resolver (for 'auto run' of an added task)
     */
    @Nullable
    protected WorkflowTaskInstance runTask(@Nonnull final ServiceContext serviceContext,
                                           @Nonnull WorkflowTaskInstance taskInstance,
                                           @Nullable final String optionKey, @Nullable final String comment,
                                           @Nullable final Map<String, Object> data,
                                           @Nullable final MetaData actionMetaData,
                                           boolean commit)
            throws PersistenceException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("runTask; '{}.{}'...", taskInstance, optionKey);
        }
        final MetaData metaData = getMetaData(actionMetaData, serviceContext, null, taskInstance);
        Resource runningFolder = giveInstanceFolder(serviceContext, getTenantId(taskInstance.getResource()),
                WorkflowTaskInstance.State.running);
        Resource moved = serviceContext.getResolver().move(taskInstance.getPath(), runningFolder.getPath());
        taskInstance = loadInstance(serviceContext, moved.getPath());
        if (taskInstance != null) {
            addTaskComment(serviceContext, taskInstance, comment);
            changeTaskData(serviceContext, taskInstance, PP_DATA, data, metaData);
            Map<String, Object> opData = new HashMap<>();
            if (StringUtils.isNotBlank(optionKey)) {
                opData.put(PN_CHOSEN_OPTION, optionKey);
            }
            opData.put(PN_EXECUTED, Calendar.getInstance());
            opData.put(PN_EXECUTED_BY, metaData.get(META_USER_ID));
            changeTaskData(serviceContext, taskInstance, null, opData, metaData);
            if (commit) {
                serviceContext.getResolver().commit();
            }
            WorkflowAction.Result result = processAction(serviceContext, taskInstance, optionKey, comment, metaData);
            if (result.getStatus() != WorkflowAction.Status.failure) {
                taskInstance = finishTask(serviceContext, taskInstance,
                        result.getStatus() == WorkflowAction.Status.cancel, null, null, metaData);
            }
        } else {
            LOG.error("task instance can't be moved to state folder: '{}'", moved);
        }
        return taskInstance;
    }

    WorkflowAction.Result processAction(@Nonnull final ServiceContext serviceContext,
                                        @Nonnull final WorkflowTaskInstance taskInstance,
                                        @Nullable final String optionKey, @Nullable final String comment,
                                        @Nonnull final MetaData actionMetaData) {
        WorkflowAction.Result result = new WorkflowAction.Result();
        WorkflowTaskTemplate.Option option = taskInstance.getTemplate().getOption(optionKey);
        String topic = taskInstance.getTopic();
        if (StringUtils.isNotBlank(topic)) {
            result.merge(processAction(topic, serviceContext.getRequestContext(), taskInstance, option, comment, actionMetaData));
        }
        if (option != null && result.getStatus() == WorkflowAction.Status.success) {
            topic = option.getTopic();
            if (StringUtils.isNotBlank(topic)) {
                result.merge(processAction(topic, serviceContext.getRequestContext(), taskInstance, option, comment, actionMetaData));
            }
            if (result.getStatus() == WorkflowAction.Status.success) {
                WorkflowTaskTemplate template = option.getTemplate();
                if (template != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("creating next task; '{}.{}' -> '{}'...", taskInstance, option.getName(), template.getPath());
                    }
                    WorkflowTaskInstance added = addTask(serviceContext, null,
                            taskInstance.getPath(), template.getPath(),
                            taskInstance.getTarget(), option.getData(),
                            comment, actionMetaData);
                    if (added == null) {
                        LOG.error("creation of next task of template '{}' failed", template.getPath());
                        return new WorkflowAction.Result(WorkflowAction.Status.failure);
                    }
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("no following task declared; '{}.{}'", taskInstance, option.getName());
                    }
                }
            }
        }
        return result;
    }

    WorkflowAction.Result processAction(@Nonnull final String topic,
                                        @Nonnull final BeanContext context,
                                        @Nonnull final WorkflowTaskInstance taskInstance,
                                        @Nullable final WorkflowTaskTemplate.Option option,
                                        @Nullable final String comment,
                                        @Nonnull final MetaData actionMetaData) {
        WorkflowAction.Result result = new WorkflowAction.Result();
        List<WorkflowActionManager.ActionReference> action = actionManager.getWorkflowAction(topic);
        if (LOG.isDebugEnabled()) {
            LOG.debug("processAction; '{}' ({}{})...",
                    topic, taskInstance, option != null ? "." + option.getName() : "");
        }
        if (action != null) {
            for (WorkflowActionManager.ActionReference reference : action) {
                if (result.getStatus() != WorkflowAction.Status.success) {
                    LOG.error("execution aborted ({})", result);
                    break;
                }
                try {
                    result.merge(reference.getAction().process(context,
                            taskInstance, option, comment, actionMetaData));
                } catch (Exception ex) {
                    LOG.error(ex.toString());
                    result.setStatus(WorkflowAction.Status.failure);
                    result.add(new WorkflowAction.Message(WorkflowAction.Level.error, ex.toString()));
                }
            }
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("processAction; '{}' ({}{}): {}",
                    topic, taskInstance, option != null ? "." + option.getName() : "", result);
        }
        return result;
    }

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
    @Override
    @Nullable
    public WorkflowTaskInstance finishTask(@Nonnull final BeanContext context,
                                           @Nonnull final String taskInstancePath, boolean cancelled,
                                           @Nullable final String comment, @Nullable final Map<String, Object> data,
                                           @Nullable final MetaData actionMetaData)
            throws PersistenceException {
        WorkflowTaskInstance taskInstance = loadInstance(context, taskInstancePath);
        if (taskInstance != null) {
            if (taskInstance.getState() != WorkflowTaskInstance.State.finished) {
                Session session = context.getResolver().adaptTo(Session.class);
                if (session != null) {
                    if (permissionsService.hasAllPrivileges(session, taskInstancePath, WRITE_PRIVILEGE_KEY)) {
                        try (final ServiceContext serviceContext = new ServiceContext(context)) {
                            taskInstance = finishTask(serviceContext, taskInstance, cancelled, comment, data, actionMetaData);
                        } catch (LoginException ex) {
                            LOG.error(ex.toString());
                        }
                    } else {
                        throw new PersistenceException("insufficient privileges");
                    }
                } else {
                    throw new IllegalStateException("can't adapt resolver to session");
                }
            } else {
                LOG.error("finish request for a finished task ({})", taskInstancePath);
                throw new PersistenceException("task is finished already");
            }
        } else {
            LOG.error("task instance not available: '{}'", taskInstancePath);
        }
        return taskInstance;
    }

    /**
     * finishes the execution of a workflow by finishing the given task
     *
     * @param serviceContext the current context of the service user
     * @param taskInstance   the path to the task instance ('inbox' resource)
     * @param cancelled      should be 'true' if the workflow execution is cancelled
     * @param comment        an optional comment added to the task
     * @param data           the values for the task to execute ('data' must be named as 'data/key')
     * @param actionMetaData the task meta data from the calling service
     * @return the model of the moved instance
     */
    @Nullable
    private WorkflowTaskInstance finishTask(@Nonnull final ServiceContext serviceContext,
                                            @Nonnull WorkflowTaskInstance taskInstance, boolean cancelled,
                                            @Nullable final String comment, @Nullable final Map<String, Object> data,
                                            @Nullable final MetaData actionMetaData)
            throws PersistenceException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("finishTask: '{}' ({})...", taskInstance, cancelled);
        }
        final MetaData metaData = getMetaData(actionMetaData, serviceContext, null, taskInstance);
        Resource finishedFolder = giveInstanceFolder(serviceContext, getTenantId(taskInstance.getResource()),
                WorkflowTaskInstance.State.finished);
        Resource moved = serviceContext.getResolver().move(taskInstance.getPath(), finishedFolder.getPath());
        taskInstance = loadInstance(serviceContext, moved.getPath());
        if (taskInstance != null) {
            addTaskComment(serviceContext, taskInstance, comment);
            changeTaskData(serviceContext, taskInstance, PP_DATA, data, metaData);
            Map<String, Object> opData = new HashMap<>();
            opData.put(cancelled ? PN_CANCELLED : PN_FINISHED, Calendar.getInstance());
            opData.put(cancelled ? PN_CANCELLED_BY : PN_FINISHED_BY, metaData.get(META_USER_ID));
            changeTaskData(serviceContext, taskInstance, null, opData, metaData);
            serviceContext.getResolver().commit();
            if (LOG.isInfoEnabled()) {
                LOG.info("finishTask(): {} done.", taskInstance);
            }
        } else {
            LOG.error("task instance can't be moved to state folder: '{}'", moved);
        }
        return taskInstance;
    }

    /**
     * removes a task from the task store
     *
     * @param context      the current request context (must have all privileges to remove tasks)
     * @param instancePath the path to the task instance
     */
    @Override
    public void removeTask(@Nonnull final BeanContext context, @Nonnull final String instancePath)
            throws PersistenceException {
        Resource taskResource = context.getResolver().getResource(instancePath);
        if (taskResource != null) {
            if (LOG.isInfoEnabled()) {
                LOG.info("removeTask(): {}", instancePath);
            }
            ResourceResolver resolver = context.getResolver();
            resolver.delete(taskResource);
            resolver.commit();
        } else {
            LOG.error("removeTask({}) - task not available!", instancePath);
        }
    }

    /**
     * removes all tasks of workflows finished before the date 'daysFinished' in the past
     *
     * @param context    the current request context (must have all privileges to remove tasks)
     * @param daysToKeep the number of days to keep finished workflows
     */
    @Override
    public void purgeTasks(@Nonnull final BeanContext context, int daysToKeep)
            throws PersistenceException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("purgeTasks ({}) ...", daysToKeep);
        }
        ResourceResolver resolver = context.getResolver();
        Calendar now = Calendar.getInstance();
        Calendar dueDate = new GregorianCalendar();
        dueDate.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        dueDate.add(Calendar.DAY_OF_MONTH, -daysToKeep);
        List<Workflow> workflowsToPurge = new ArrayList<>();
        String query = "/jcr:root" + config.workflow_root() + "/*/" + WorkflowTaskInstance.State.finished + "/*";
        @SuppressWarnings("deprecation")
        Iterator<Resource> finished = resolver.findResources(query, Query.XPATH);
        while (finished.hasNext()) {
            Resource taskResource = finished.next();
            Workflow workflow = loadWorkflow(context, taskResource.getPath());
            if (workflow == null || workflow.isHollow()) {
                LOG.error("can't load workflow of task '{}'", taskResource.getPath());
            } else {
                if (workflow.isFinished()) {
                    Calendar finishedDate = workflow.getFinishedDate();
                    if (finishedDate != null && finishedDate.before(dueDate)) {
                        if (!workflowsToPurge.contains(workflow)) {
                            workflowsToPurge.add(workflow);
                        }
                    }
                }
            }
        }
        for (Workflow workflow : workflowsToPurge) {
            LOG.info("purging workflow starting with '{}'...", workflow.getFirstTask().getPath());
            for (WorkflowTaskInstance task : workflow.getInstances()) {
                removeTask(context, task.getPath());
            }
        }
    }

    //

    /**
     * add a comment if comment is not empty
     *
     * @param context      the current request context
     * @param taskInstance the task instance to change
     * @param comment      the comment text
     */
    protected void addTaskComment(@Nonnull final ServiceContext context,
                                  @Nonnull final WorkflowTaskInstance taskInstance, @Nullable final String comment)
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
            resolver.create(comments, "wfc-" + UUID.randomUUID().toString(), commentValues);
            resolver.commit();
        }
    }

    /**
     * changes the properties of the task instanmce
     *
     * @param context the current request context
     * @param task    the task instance to change
     * @param data    the properties to change / to remove (removed if value is 'null')
     * @param meta    the current operation meta data (tenant, user, ...)
     */
    protected void changeTaskData(@Nonnull final BeanContext context,
                                  @Nonnull final WorkflowTaskInstance task, @Nullable final String subPath,
                                  @Nullable final Map<String, Object> data, @Nullable final MetaData meta)
            throws PersistenceException {
        Resource resource = task.getResource();
        if (StringUtils.isNotBlank(subPath)) {
            resource = resource.getChild(subPath);
        }
        if (resource != null) {
            ModifiableValueMap values = resource.adaptTo(ModifiableValueMap.class);
            if (values != null) {
                changeProperties(values, data, meta);
            } else {
                throw new PersistenceException("can't modify properties of '" + task.getResource().getPath() + "'");
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
                if (value != null && !DATA_KEY_REMOVE.equals(value)) {
                    if (meta != null) {
                        if (value instanceof String) {
                            value = meta.getValue((String) value);
                        } else if (value instanceof String[]) {
                            String[] multi = (String[]) value;
                            for (int i = 0; i < multi.length; i++) {
                                multi[i] = meta.getValue(multi[i]);
                            }
                        }
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
    private class ServiceContext extends BeanContext.Wrapper implements Closeable {

        protected ServiceContext(@Nonnull BeanContext requestContext) throws LoginException {
            this(requestContext, resolverFactory.getServiceResourceResolver(null));
        }

        protected ServiceContext(@Nonnull BeanContext requestContext, @Nonnull ResourceResolver serviceResolver) {
            super(requestContext, serviceResolver);
        }

        public BeanContext getRequestContext() {
            return beanContext;
        }

        public String getUserId() {
            return getRequestContext().getResolver().getUserID();
        }

        @Override
        public void close() {
            resolver.close();
        }
    }

    private MetaData getMetaData(@Nullable MetaData metaData, @Nonnull final ServiceContext context,
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

    // BeanFactory

    @Nonnull
    @Override
    public SlingBean createBean(@Nonnull BeanContext context, @Nonnull Resource resource,
                                @Nonnull Class<? extends SlingBean> type)
            throws InstantiationException {
        SlingBean result = null;
        SlingHttpServletRequest request = context.getRequest();
        // use service resolver to build the entire graph even if the access is not allowed
        Workflow workflow = (Workflow) request.getAttribute(RA_WORKFLOW);
        if (WorkflowTaskInstance.class.isAssignableFrom(type)) {
            result = workflow != null
                    ? loadInstanceRef(context, resource.getPath())
                    : loadInstance(context, resource.getPath());
        } else if (WorkflowTaskTemplate.class.isAssignableFrom(type)) {
            result = workflow != null
                    ? loadTemplateRef(context, resource.getPath())
                    : loadTemplate(context, resource.getPath());
        } else if (Workflow.class.isAssignableFrom(type)) {
            result = workflow != null ? workflow : loadWorkflow(context, resource.getPath());
        }
        if (result == null) {
            throw new InstantiationException("can't create instance of '" + type + "' for resource '" + resource.getPath() + "'");
        }
        return result;
    }

    protected class ServiceTaskInstance extends WorkflowTaskInstance {

        public ServiceTaskInstance(WorkflowTaskTemplate template, State state) {
            super(template, state);
        }

        @Override
        protected WorkflowTaskInstance getTask(String propertyName) {
            String taskRefId = getProperty(propertyName, String.class);
            return StringUtils.isNotBlank(taskRefId) ? loadInstanceRef(context, taskRefId) : null;
        }

        /**
         * @return 'true' if it's allowed to cancel the task processing
         */
        @Override
        public boolean isCancellingAllowed() {
            Session session;
            return state != State.finished
                    && (session = context.getResolver().adaptTo(Session.class)) != null
                    && permissionsService.hasAllPrivileges(session, getResource().getPath(), WRITE_PRIVILEGE_KEY);
        }

        /**
         * @return 'true' if it's possible to show a workflow graph from this instance
         */
        @Override
        public boolean isGraphAvailable() {
            return !(getTemplate() instanceof ServiceTaskTemplateRef);
        }
    }

    protected class ServiceTaskInstanceRef extends ServiceTaskInstance {

        public ServiceTaskInstanceRef(WorkflowTaskTemplate template, State state) {
            super(template, state);
        }

        @Override
        @Nonnull
        public ValueMap getData() {
            return new ValueMapDecorator(Collections.emptyMap());
        }
    }

    protected class ServiceTaskTemplate extends WorkflowTaskTemplate {

        protected boolean isLoop = false; // controlled by the referencing option

        @Override
        @Nonnull
        public Option createOption(Resource resource) {
            return new ServiceOption(resource);
        }

        @Override
        public boolean isWorkflowLoop() {
            return isLoop;
        }

        protected class ServiceOption extends Option {

            private transient ServiceTaskTemplate template;

            public ServiceOption(@Nonnull Resource resource) {
                super(resource);
            }

            @Override
            @Nullable
            public WorkflowTaskTemplate getTemplate() {
                if (template == null) {
                    // lazy load to prevent from stack overflow on task loops
                    template = StringUtils.isNotBlank(templatePath)
                            ? loadTemplateRef(context instanceof ServiceContext
                            ? ((ServiceContext) context).getRequestContext() : context, templatePath) : null;
                    if (template != null && isLoop) {
                        template.isLoop = true;
                    }
                }
                return template;
            }

            @Override
            public void setIsLoop(boolean isLoop) {
                this.isLoop = isLoop;
                template.isLoop = isLoop;
            }
        }
    }

    protected class ServiceTaskTemplateRef extends ServiceTaskTemplate {

        @Override
        @Nonnull
        public ValueMap getData() {
            return new ValueMapDecorator(Collections.emptyMap());
        }

        @Nonnull
        public String getDialog() {
            return DEFAULT_DIALOG;
        }
    }

    protected class ServiceWorkflow extends Workflow {

        protected LinkedHashMap<String, WorkflowTask> tasks = new LinkedHashMap<>();
        protected boolean restricted = false;

        @Override
        public boolean isRestricted() {
            return restricted;
        }

        @Override
        @Nonnull
        public LinkedHashMap<String, WorkflowTask> getTasks() {
            return tasks;
        }

        @Override
        protected void addInstance(@Nonnull WorkflowTaskInstance task) {
            String name = task.getName();
            if (tasks.containsKey(name)) {
                throw new IllegalStateException("workflow contains instance '" + name + "' twice");
            }
            tasks.put(name, task);
            if (task instanceof ServiceTaskInstanceRef) {
                restricted = true;
            }
        }

        @Override
        protected void addTemplate(@Nonnull WorkflowTaskTemplate template, @Nonnull String key) {
            tasks.put(key, template);
            if (template instanceof ServiceTaskTemplateRef) {
                restricted = true;
            }
        }

        @Override
        protected WorkflowService getService() {
            return PlatformWorkflowService.this;
        }
    }

    @Nullable
    protected ServiceTaskInstance loadInstance(@Nonnull final BeanContext context,
                                               @Nonnull final String pathOrId) {
        ServiceTaskInstance taskInstance = null;
        Resource resource = getTaskResource(context, pathOrId);
        if (resource != null) {
            taskInstance = new ServiceTaskInstance(loadTemplateRef(context,
                    resource.getValueMap().get(PN_TEMPLATE, "")), getState(resource));
            taskInstance.initialize(context, resource);
        }
        return taskInstance;
    }

    /**
     * a reference to ain instance should be accessible even if the user has no access
     */
    @Nullable
    protected ServiceTaskInstance loadInstanceRef(@Nonnull final BeanContext context,
                                                  @Nonnull final String pathOrId) {
        ServiceTaskInstance taskInstance = loadInstance(context, pathOrId);
        if (taskInstance == null) {
            try (final ServiceContext serviceContext = new ServiceContext(context instanceof ServiceContext
                    ? ((ServiceContext) context).getRequestContext() : context)) {
                Resource resource = getTaskResource(serviceContext, pathOrId);
                if (resource != null) {
                    taskInstance = new ServiceTaskInstanceRef(loadTemplateRef(context,
                            resource.getValueMap().get(PN_TEMPLATE, "")), getState(resource));
                    taskInstance.initialize(context, resource);
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
        return taskInstance;
    }

    @Nullable
    protected ServiceTaskTemplate loadTemplate(@Nonnull final BeanContext context,
                                               @Nonnull final String path) {
        ServiceTaskTemplate taskTemplate = null;
        Resource resource = getTaskResource(context, path);
        if (resource != null) {
            taskTemplate = new ServiceTaskTemplate();
            taskTemplate.initialize(context, resource);
        }
        return taskTemplate;
    }

    /**
     * a reference to a template should be accessible even if the user has no access
     */
    @Nullable
    protected ServiceTaskTemplate loadTemplateRef(@Nonnull final BeanContext context,
                                                  @Nonnull final String path) {
        ServiceTaskTemplate taskTemplate = loadTemplate(context, path);
        if (taskTemplate == null) {
            try (final ServiceContext serviceContext = new ServiceContext(context instanceof ServiceContext
                    ? ((ServiceContext) context).getRequestContext() : context)) {
                Resource resource = getTaskResource(serviceContext, path);
                if (resource != null) {
                    taskTemplate = new ServiceTaskTemplateRef();
                    taskTemplate.initialize(context, resource);
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
        return taskTemplate;
    }

    @Nullable
    protected ServiceWorkflow loadWorkflow(@Nonnull final BeanContext context,
                                           @Nonnull final String path) {
        ServiceWorkflow workflow = null;
        Resource resource = getTaskResource(context, path);
        if (resource != null) {
            workflow = new ServiceWorkflow();
            workflow.initialize(context, resource);
        }
        return workflow;
    }

    @Nonnull
    private Resource giveInstanceFolder(@Nonnull final ServiceContext context, @Nullable final String tenantId,
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

    protected Resource getTaskResource(@Nonnull final BeanContext context, @Nonnull String pathOrId) {
        Resource resource = null;
        if (StringUtils.isNotBlank(pathOrId = pathOrId.trim())) {
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
        }
        return resource;
    }

    protected String adjustAssignee(BeanContext context, String assignee, Class<? extends Authorizable> type) {
        if (StringUtils.isNotBlank(assignee)) {
            try {
                UserManager userManager = ((JackrabbitSession) Objects.requireNonNull(
                        context.getResolver().adaptTo(Session.class))).getUserManager();
                if (type.isInstance(userManager.getAuthorizable(assignee))) {
                    return assignee;
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
        return null;
    }
}
