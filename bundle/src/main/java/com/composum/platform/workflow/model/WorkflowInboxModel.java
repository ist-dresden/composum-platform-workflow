package com.composum.platform.workflow.model;

import org.apache.sling.api.SlingHttpServletRequest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class WorkflowInboxModel extends WorkflowServiceModel {

    public static final String PARAM_SCOPE = "scope";

    public static final String SA_INBOX_SCOPE = "workflow.inbox.scope";

    private transient WorkflowTaskInstance.State scope;
    private transient Collection<WorkflowTaskInstance> tasks;

    public Collection<WorkflowTaskInstance> getTasks() {
        if (tasks == null) {
            tasks = new ArrayList<>();
            Iterator<WorkflowTaskInstance> iterator = getService().findTasks(context, getTenantId(), getScope());
            while (iterator.hasNext()) {
                tasks.add(iterator.next());
            }
        }
        return tasks;
    }

    public WorkflowTaskInstance.State getScope() {
        if (scope == null) {
            SlingHttpServletRequest request = context.getRequest();
            if (request != null) {
                if (scope == null) {
                    try {
                        scope = WorkflowTaskInstance.State.valueOf(getRequest().getParameter(PARAM_SCOPE));
                        request.getSession(true).setAttribute(SA_INBOX_SCOPE, scope.name());
                    } catch (Exception ignore) {
                    }
                }
                if (scope == null) {
                    try {
                        scope = WorkflowTaskInstance.State.valueOf(
                                (String) request.getSession(true).getAttribute(SA_INBOX_SCOPE));
                    } catch (Exception ignore) {
                    }
                }
            }
            if (scope == null) {
                scope = WorkflowTaskInstance.State.pending;
            }
        }
        return scope;
    }
}
