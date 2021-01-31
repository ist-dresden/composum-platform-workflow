package com.composum.platform.workflow;

import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.sling.core.BeanContext;
import org.apache.sling.api.resource.ValueMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * the interface of a workflow process action - a service which is providing the behaviour of a workflow task
 */
public interface WorkflowAction extends WorkflowTopic {

    /**
     * executes the action of a task with the chosen option
     *
     * @param context the current request context (user session)
     * @param task    the task to process
     * @param option  the chosen option
     * @param data    the task data value union
     */
    @Nonnull
    Result process(@Nonnull BeanContext context, @Nonnull WorkflowTaskInstance task,
                   @Nullable WorkflowTaskTemplate.Option option, @Nonnull ValueMap data)
            throws Exception;
}
