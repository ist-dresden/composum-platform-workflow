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
                        // a parameter 'scope' is declaring the session attribute to remember the scope
                        scope = WorkflowTaskInstance.State.valueOf(getRequest().getParameter(PARAM_SCOPE));
                        request.getSession(true).setAttribute(SA_INBOX_SCOPE, scope.name());
                    } catch (Exception ignore) {
                    }
                }
                if (scope == null) {
                    try {
                        // a selector 'scope' is overrriding the scope stored on the session
                        for (String selector : request.getRequestPathInfo().getSelectors()) {
                            try {
                                scope = WorkflowTaskInstance.State.valueOf(selector);
                            } catch (Exception ignore) {
                            }
                        }
                    } catch (Exception ignore) {
                    }
                }
                if (scope == null) {
                    try {
                        // use scope stored in the session if no parameter and no selector found
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
