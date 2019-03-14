package com.composum.platform.workflow.servlet;

import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import com.composum.sling.core.ResourceHandle;
import com.composum.sling.core.servlet.AbstractServiceServlet;
import com.composum.sling.core.servlet.ServletOperation;
import com.composum.sling.core.servlet.ServletOperationSet;
import com.composum.sling.cpnl.CpnlElFunctions;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.composum.platform.workflow.model.WorkflowTask.PP_DATA;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

/**
 * The servlet to provide workflow task management.
 */
@Component(service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Platform Workflow Servlet",
                ServletResolverConstants.SLING_SERVLET_PATHS + "=/bin/cpm/platform/workflow",
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET,
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_POST
        })
@SuppressWarnings("Duplicates")
public class WorkflowServlet extends AbstractServiceServlet {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowServlet.class);

    public static final String PARAM_TENANT_ID = "tenant.id";
    public static final String PARAM_TEMPLATE = "wf.template";
    public static final String PARAM_OPTION = "wf.option";
    public static final String PARAM_COMMENT = "wf.comment";

    @Reference
    protected WorkflowService workflowService;

    protected BundleContext bundleContext;

    @Activate
    @Modified
    protected void activate(ComponentContext context) {
        this.bundleContext = context.getBundleContext();
    }

    //
    // Servlet operations
    //

    public enum Extension {
        json
    }

    public enum Operation {
        dialog, addTask, runTask
    }

    protected TenantsOperationSet operations = new TenantsOperationSet();

    protected ServletOperationSet getOperations() {
        return operations;
    }

    @Override
    protected boolean isEnabled() {
        return true;
    }

    /** setup of the servlet operation set for this servlet instance */
    @Override
    public void init() throws ServletException {
        super.init();

        // GET
        operations.setOperation(ServletOperationSet.Method.GET, Extension.json,
                Operation.dialog, new GetDialogOperation());

        // POST
        operations.setOperation(ServletOperationSet.Method.POST, Extension.json,
                Operation.addTask, new AddTaskOperation());
        operations.setOperation(ServletOperationSet.Method.POST, Extension.json,
                Operation.runTask, new RunTaskOperation());
    }

    public class TenantsOperationSet extends ServletOperationSet<Extension, Operation> {

        public TenantsOperationSet() {
            super(Extension.json);
        }
    }

    // Dialog

    public class GetDialogOperation implements ServletOperation {

        @Override
        public void doIt(@Nonnull final SlingHttpServletRequest request,
                         @Nonnull final SlingHttpServletResponse response,
                         @Nonnull final ResourceHandle resource)
                throws ServletException, IOException {
            BeanContext context = new BeanContext.Servlet(getServletContext(), bundleContext, request, response);
            WorkflowTaskInstance task = workflowService.getInstance(context, resource.getPath());
            if (task != null) {
                WorkflowTaskTemplate template = task.getTemplate();
                if (template != null) {
                    RequestDispatcherOptions options = new RequestDispatcherOptions();
                    options.setForceResourceType(template.getDialog());
                    RequestDispatcher dispatcher = request.getRequestDispatcher(resource, options);
                    if (dispatcher != null) {
                        dispatcher.forward(request, response);
                    } else {
                        response.sendError(SC_INTERNAL_SERVER_ERROR,
                                "can't forward request '" + request.getRequestPathInfo().getSuffix() + "'");
                    }
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                            "invalid task '" + resource.getPath() + "'");
                }
            } else {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "no task found at '" + resource.getPath() + "'");
            }
        }
    }

    // Task execution

    public class AddTaskOperation implements ServletOperation {

        @Override
        public void doIt(@Nonnull final SlingHttpServletRequest request,
                         @Nonnull final SlingHttpServletResponse response,
                         @Nonnull final ResourceHandle resource)
                throws IOException {
            try {
                BeanContext context = new BeanContext.Servlet(getServletContext(), bundleContext, request, response);
                String tenantId = request.getParameter(PARAM_TENANT_ID);
                String taskTemplate = request.getParameter(PARAM_TEMPLATE);
                String comment = request.getParameter(PARAM_COMMENT);
                WorkflowTaskInstance taskInstance = workflowService.addTask(context, tenantId, null,
                        taskTemplate, comment, getTaskData(request), null);
                if (taskInstance != null) {
                    jsonStatus(request, response, true,
                            taskInstance.getTemplate().getHintAdded("task created"), null);
                } else {
                    jsonStatus(request, response, false, "task not created", null);
                }
            } catch (PersistenceException ex) {
                LOG.error(ex.getMessage(), ex);
                jsonStatus(request, response, false, ex.getMessage(), null);
            }
        }
    }

    public class RunTaskOperation implements ServletOperation {

        @Override
        public void doIt(@Nonnull final SlingHttpServletRequest request,
                         @Nonnull final SlingHttpServletResponse response,
                         @Nonnull final ResourceHandle resource)
                throws IOException {
            try {
                BeanContext context = new BeanContext.Servlet(getServletContext(), bundleContext, request, response);
                String optionKey = request.getParameter(PARAM_OPTION);
                String comment = request.getParameter(PARAM_COMMENT);
                WorkflowTaskInstance taskInstance = workflowService.runTask(context, resource.getPath(),
                        optionKey, comment, getTaskData(request), null);
                if (taskInstance != null) {
                    WorkflowTaskTemplate.Option option = taskInstance.getTemplate().getOption(optionKey);
                    jsonStatus(request, response, true, option != null
                            ? option.getHintSelected("task option '" + optionKey + "' choosen")
                            : "invalid option!? '" + optionKey + "'", null);
                } else {
                    jsonStatus(request, response, false, "task job creation failed", null);
                }
            } catch (PersistenceException ex) {
                LOG.error(ex.getMessage(), ex);
                jsonStatus(request, response, false, ex.getMessage(), null);
            }
        }
    }

    protected Map<String, Object> getTaskData(@Nonnull final SlingHttpServletRequest request) {
        Map<String, Object> data = new HashMap<>();
        for (RequestParameter parameter : request.getRequestParameterList()) {
            String name = parameter.getName();
            if (name.startsWith(PP_DATA + "/")) {
                data.put(name.substring(PP_DATA.length() + 1), parameter.getString());
            }
        }
        return data;
    }

    // JSON answer

    public static class Message {

        public final int level;
        public final String text;
        public final String hint;

        public Message(int level, String text, String hint) {
            this.level = level;
            this.text = text;
            this.hint = hint;
        }
    }

    protected void jsonStatus(@Nonnull final SlingHttpServletRequest request,
                              @Nonnull final SlingHttpServletResponse response,
                              final boolean success, @Nullable final String text,
                              @Nullable final Collection<Message> messages)
            throws IOException {
        if (success) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("{}:{} successful: '{}'", request.getMethod(), request.getRequestURI(), text);
            }
        } else {
            LOG.warn("{}:{} failed: '{}'", request.getMethod(), request.getRequestURI(), text);
        }
        response.setStatus(success ? SC_OK : SC_INTERNAL_SERVER_ERROR);
        response.setContentType("application/json; charset=UTF-8");
        JsonWriter writer = new JsonWriter(response.getWriter());
        writer.beginObject();
        writer.name("success").value(success);
        if (StringUtils.isNotBlank(text)) {
            writer.name("response").beginObject();
            writer.name("level").value(success ? "info" : "error");
            writer.name("text").value(CpnlElFunctions.i18n(request, text));
            writer.endObject();
        }
        if (messages != null) {
            writer.name("messages").beginArray();
            for (Message message : messages) {
                writer.beginObject();
                writer.name("level").value("error");
                writer.name("text").value(CpnlElFunctions.i18n(request, message.text));
                writer.name("hint").value(CpnlElFunctions.i18n(request, message.hint));
                writer.endObject();
            }
            writer.endArray();
        }
        writer.endObject();
    }
}
