package com.composum.platform.workflow;

import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.sling.core.BeanContext;
import org.apache.sling.api.resource.ValueMap;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * the interface of a workflow process action - a service which is providing the behaviour of a workflow task
 */
public interface WorkflowValidator extends WorkflowTopic {

    /**
     * executes the validation of a task to create
     *
     * @param context  the current request context (user session)
     * @param template the template of the task to add
     * @param target   the set of target objects
     * @param taskData the prepared task data value union
     */
    @Nonnull
    Result validate(@Nonnull BeanContext context, @Nonnull WorkflowTaskTemplate template,
                    @Nonnull List<String> target, @Nonnull ValueMap taskData);
}
