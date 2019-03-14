package com.composum.platform.workflow.model;

import com.composum.platform.models.simple.SimpleModel;
import com.composum.platform.workflow.service.WorkflowService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class WorkflowInboxModel extends SimpleModel {

    private transient String tenantId;
    private transient Collection<WorkflowTaskInstance> tasks;

    private transient WorkflowService service;

    public String getTenantId() {
        if (tenantId == null) {
            tenantId = getService().getTenantId(getResource());
        }
        return tenantId;
    }

    public Collection<WorkflowTaskInstance> getTasks() {
        if (tasks == null) {
            tasks = new ArrayList<>();
            Iterator<WorkflowTaskInstance> iterator = getService().findTasks(context, getTenantId());
            while (iterator.hasNext()) {
                tasks.add(iterator.next());
            }
        }
        return tasks;
    }

    protected WorkflowService getService() {
        if (service == null) {
            service = context.getService(WorkflowService.class);
        }
        return service;
    }
}
