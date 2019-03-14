package com.composum.platform.workflow.model;

import com.composum.platform.models.simple.SimpleModel;
import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.cpnl.CpnlElFunctions;
import org.apache.commons.lang3.StringUtils;

public class WorkflowDialogModel extends SimpleModel {

    public static final String RUN_OPTION_URI = "/bin/cpm/platform/workflow.runTask.json";

    private transient WorkflowTaskInstance task;

    protected String alertKey;
    protected String alertText;

    private transient WorkflowDialogAction action;

    private transient WorkflowService workflowService;

    public String i18n(String text) {
        return CpnlElFunctions.i18n(request, text);
    }

    protected WorkflowService getWorkflowService() {
        if (workflowService == null) {
            workflowService = context.getService(WorkflowService.class);
        }
        return workflowService;
    }

    public WorkflowTaskInstance getTask() {
        if (task == null) {
            task = getWorkflowService().getInstance(context, resource.getPath());
        }
        return task;
    }

    @Override
    public String getTitle() {
        return getTask().getTitle();
    }

    // initial alert message

    public boolean isAlertSet() {
        return StringUtils.isNotBlank(alertKey);
    }

    public String getAlertKey() {
        return isAlertSet() ? alertKey : "warning hidden";
    }

    public String getAlertText() {
        return isAlertSet() ? i18n(alertText) : "";
    }

    // submit action

    public WorkflowDialogAction getDefaultAction() {
        return new RunTaskOptionAction();
    }

    public WorkflowDialogAction getAction() {
        if (action == null) {
            action = getDefaultAction();
        }
        return action;
    }

    public interface WorkflowDialogAction {

        String getUrl();

        String getMethod();

        String getEncType();
    }

    public class RunTaskOptionAction implements WorkflowDialogAction {

        public String getUrl() {
            return getRequest().getContextPath() + RUN_OPTION_URI + getResource().getPath();
        }

        public String getMethod() {
            return "POST";
        }

        public String getEncType() {
            return "multipart/form-data";
        }
    }
}
