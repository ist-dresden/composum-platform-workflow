package com.composum.platform.workflow.action;

import com.composum.platform.models.simple.MetaData;
import com.composum.platform.workflow.WorkflowAction;
import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import org.apache.commons.lang3.StringUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.composum.platform.workflow.action.GenericWorkflowAction.TOPIC_GENERIC;

/**
 * a simple 'forwarder' to the next step of a workflow task; creates a task from the template of the chosen option -
 * the next task is not created during this action execution if the option has a 'topic' declared - in this case
 * the action(s) referenced by the topic has to perform the workflow forwarding
 */
@Component(
        property = {
                Constants.SERVICE_DESCRIPTION + "=Generic Workflow Job",
                WorkflowAction.PROPERTY_TOPICS + "=" + TOPIC_GENERIC
        }
)
public class GenericWorkflowAction implements WorkflowAction {

    private static final Logger LOG = LoggerFactory.getLogger(GenericWorkflowAction.class);

    public static final String TOPIC_GENERIC = "composum/platform/workflow/generic";

    @Reference
    protected WorkflowService workflowService;

    @Override
    @Nonnull
    public Result process(@Nonnull final BeanContext context,
                          @Nonnull final WorkflowTaskInstance task,
                          @Nullable final WorkflowTaskTemplate.Option option, @Nullable final String comment,
                          @Nonnull final MetaData metaData) {
        if (option != null) {
            if (StringUtils.isBlank(option.getTopic())) {
                WorkflowTaskTemplate template = option.getTemplate();
                if (template != null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("creating next task; '{}.{}' -> '{}'...", task, option.getName(), template.getPath());
                    }
                    WorkflowTaskInstance added = workflowService.addTask(context, null,
                            task.getPath(), template.getPath(), comment, null, metaData);
                    if (added == null) {
                        LOG.error("creation of next task of template '{}' failed", template.getPath());
                        return new Result(Status.failure);
                    }
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("no following task declared; '{}.{}'", task, option.getName());
                    }
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("option '{}.{}' has declared topic '{}'", task, option.getName(), option.getTopic());
                }
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("no option chosen ({})", task);
            }
        }
        return Result.OK;
    }
}
