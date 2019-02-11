package com.composum.platform.workflow.model;

import com.composum.platform.models.simple.SimpleModel;
import com.composum.platform.workflow.service.WorkflowService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class WorkflowInboxModel extends SimpleModel {

    Collection<WorkflowTaskInstance> tasks;

    public Collection<WorkflowTaskInstance> getTasks() {
        if (tasks == null) {
            tasks = new ArrayList<>();
            WorkflowService service = context.getService(WorkflowService.class);
            Iterator<WorkflowTaskInstance> iterator = service.findTasks(context.getResolver(), null);
            while (iterator.hasNext()) {
                tasks.add(iterator.next());
            }
        }
        return tasks;
    }
}
