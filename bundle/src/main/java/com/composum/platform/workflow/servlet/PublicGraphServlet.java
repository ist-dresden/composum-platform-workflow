package com.composum.platform.workflow.servlet;

import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import com.composum.sling.core.ResourceHandle;
import com.composum.sling.core.Restricted;
import com.composum.sling.core.servlet.AbstractServiceServlet;
import com.composum.sling.core.servlet.ServletOperation;
import com.composum.sling.core.servlet.ServletOperationSet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.wrappers.SlingHttpServletRequestWrapper;
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
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collections;

import static com.composum.platform.workflow.model.WorkflowTaskTemplate.TEMPLATE_TYPE;
import static com.composum.platform.workflow.servlet.WorkflowServlet.SERVICE_KEY;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

/**
 * The servlet to visualize workflow template graphs.
 */
@Component(service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Platform Workflow Graph Servlet",
                ServletResolverConstants.SLING_SERVLET_PATHS + "=/bin/public/workflow",
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        })
@Restricted(key = SERVICE_KEY)
public class PublicGraphServlet extends AbstractServiceServlet {

    private static final Logger LOG = LoggerFactory.getLogger(PublicGraphServlet.class);

    @Reference
    protected ResourceResolverFactory resolverFactory;

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
        html
    }

    public enum Operation {
        graph
    }

    protected TenantsOperationSet operations = new TenantsOperationSet();

    protected ServletOperationSet getOperations() {
        return operations;
    }

    @Deprecated
    protected boolean isEnabled() {
        return true;
    }

    /**
     * setup of the servlet operation set for this servlet instance
     */
    @Override
    public void init() throws ServletException {
        super.init();

        // GET
        operations.setOperation(ServletOperationSet.Method.GET, Extension.html,
                Operation.graph, new ForwardToGraphOperation());
    }

    public class TenantsOperationSet extends ServletOperationSet<Extension, Operation> {

        public TenantsOperationSet() {
            super(Extension.html);
        }
    }

    // Graph (templates only)

    protected class RenderingRequestWrapper extends SlingHttpServletRequestWrapper {

        protected final ResourceResolver resolver;

        public RenderingRequestWrapper(@Nonnull SlingHttpServletRequest request, @Nonnull ResourceResolver resolver) {
            super(request);
            this.resolver = resolver;
        }

        @Override
        @Nonnull
        public ResourceResolver getResourceResolver() {
            return resolver;
        }
    }

    public class ForwardToGraphOperation implements ServletOperation {

        @Override
        public void doIt(@Nonnull final SlingHttpServletRequest request,
                         @Nonnull final SlingHttpServletResponse response,
                         @Nonnull final ResourceHandle resource)
                throws ServletException, IOException {
            BeanContext context = new BeanContext.Servlet(getServletContext(), bundleContext, request, response);
            if (resource.isValid() && resource.isResourceType(TEMPLATE_TYPE)) {
                try (ResourceResolver renderResolver = resolverFactory.getServiceResourceResolver(
                        Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "rendering"))) {
                    RequestDispatcherOptions options = new RequestDispatcherOptions();
                    options.setReplaceSelectors("page");
                    RequestDispatcher dispatcher = request.getRequestDispatcher(resource, options);
                    if (dispatcher != null) {
                        RenderingRequestWrapper requestWrapper = new RenderingRequestWrapper(request, renderResolver);
                        dispatcher.forward(requestWrapper, response);
                    } else {
                        LOG.error("can't forward request to '{}.page.html'", resource.getPath());
                        response.sendError(SC_INTERNAL_SERVER_ERROR);
                    }
                } catch (LoginException ex) {
                    LOG.error(ex.getMessage());
                    response.sendError(SC_INTERNAL_SERVER_ERROR);
                }
            } else {
                request.getRequestProgressTracker().log("graph: not found ''{0}'' ({1},{2})",
                        resource.getPath(), resource.isValid(), resource.getResourceType());
                response.sendError(SC_NOT_FOUND);
            }
        }
    }
}

