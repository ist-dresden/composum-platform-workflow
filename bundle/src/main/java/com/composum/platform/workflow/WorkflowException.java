package com.composum.platform.workflow;

import org.apache.sling.api.resource.PersistenceException;

public class WorkflowException extends PersistenceException {

    public WorkflowException(WorkflowTopic.Result result) {
        super(getResultMessage(result));
    }

    public static String getResultMessage(WorkflowTopic.Result result) {
        StringBuilder builder = new StringBuilder();
        for (WorkflowTopic.Message msg : result.getMessages()) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(msg.toString());
        }
        return builder.toString();
    }
}
