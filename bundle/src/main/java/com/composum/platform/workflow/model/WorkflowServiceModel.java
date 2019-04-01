package com.composum.platform.workflow.model;

import com.composum.platform.models.simple.SimpleModel;
import com.composum.platform.workflow.service.WorkflowService;
import com.composum.platform.workflow.servlet.WorkflowServlet;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.tenant.Tenant;

public class WorkflowServiceModel extends SimpleModel {

    public static final String PARAM_TENANT_ID = WorkflowServlet.PARAM_TENANT_ID;

    private transient String tenantId;
    private transient WorkflowService service;

    /**
     * @return the tenant id determined from the resource or request; '' if not resolvable
     */
    public String getTenantId() {
        if (tenantId == null) {
            tenantId = "";
            RequestPathInfo pathInfo = null;
            SlingHttpServletRequest request = getRequest();
            if (request != null) {
                // 1st: check request parameter
                pathInfo = request.getRequestPathInfo();
                String param = request.getParameter(PARAM_TENANT_ID);
                if (StringUtils.isNotBlank(param)) {
                    if ("*".equals(param)) {
                        return tenantId;
                    }
                    tenantId = getService().getTenantId(context, param);
                }
            }
            if (StringUtils.isBlank(tenantId)) {
                // 2nd: try to use models resource
                Resource resource = getResource();
                Tenant tenant = resource.adaptTo(Tenant.class);
                if (tenant == null) {
                    // 3rd: try to use a request suffix (resource path)
                    if (pathInfo != null) {
                        String suffix = pathInfo.getSuffix();
                        if (StringUtils.isNotBlank(suffix)) {
                            resource = getResolver().getResource(suffix);
                            if (resource != null) {
                                tenant = resource.adaptTo(Tenant.class);
                            }
                        }
                    }
                }
                if (tenant != null) {
                    tenantId = tenant.getId();
                }
            }
        }
        return tenantId;
    }

    protected WorkflowService getService() {
        if (service == null) {
            service = context.getService(WorkflowService.class);
        }
        return service;
    }
}
