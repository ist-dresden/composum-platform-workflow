package com.composum.platform.workflow.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class WorkflowStartModel extends WorkflowServiceModel {

    private transient Collection<Workflow> workflows;

    public Collection<Workflow> getWorkflows() {
        if (workflows == null) {
            workflows = new ArrayList<>();
            Iterator<Workflow> iterator = getService().findWorkflows(context, getTenantId(), getResource());
            while (iterator.hasNext()) {
                workflows.add(iterator.next());
            }
        }
        return workflows;
    }
}
