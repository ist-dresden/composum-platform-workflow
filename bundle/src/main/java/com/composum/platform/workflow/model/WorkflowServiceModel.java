package com.composum.platform.workflow.model;

import com.composum.platform.models.simple.SimpleModel;
import com.composum.platform.workflow.service.WorkflowService;

public class WorkflowServiceModel extends SimpleModel {

    private transient String tenantId;
    private transient WorkflowService service;

    public String getTenantId() {
        if (tenantId == null) {
            tenantId = getService().getTenantId(getResource());
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
