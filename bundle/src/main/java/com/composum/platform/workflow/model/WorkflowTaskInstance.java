package com.composum.platform.workflow.model;

import com.composum.platform.workflow.service.WorkflowService;

public class WorkflowTaskInstance extends WorkflowTask {

    public static final String PN_TEMPLATE = "template";

    private transient WorkflowTaskTemplate template;

    public WorkflowTaskTemplate getTemplate() {
        if (template == null) {
            template = context.getService(WorkflowService.class).getTemplate(context.getResolver(),
                    getProperty(PN_TEMPLATE, ""));
        }
        return template;
    }

    public String getTitle(){
        return getTemplate().getTitle();
    }

    public String getHint(){
        return getTemplate().getHint();
    }
}
