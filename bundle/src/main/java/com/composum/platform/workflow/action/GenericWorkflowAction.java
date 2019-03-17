package com.composum.platform.workflow.action;

import com.composum.platform.models.simple.MetaData;
import com.composum.platform.workflow.WorkflowAction;
import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.sling.core.BeanContext;
import org.apache.commons.lang3.StringUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.composum.platform.workflow.action.GenericWorkflowAction.TOPIC_GENERIC;

@Component(
        property = {
                Constants.SERVICE_DESCRIPTION + "=Generic Workflow Job",
                WorkflowAction.PROPERTY_TOPICS + "=" + TOPIC_GENERIC
        }
)
public class GenericWorkflowAction implements WorkflowAction {

    private static final Logger LOG = LoggerFactory.getLogger(GenericWorkflowAction.class);

    public static final String TOPIC_GENERIC = "composum/platform/workflow/generic";

    @Override
    @Nonnull
    public Result process(@Nonnull final BeanContext context,
                          @Nonnull final WorkflowTaskInstance task,
                          @Nullable final WorkflowTaskTemplate.Option option, @Nullable final String comment,
                          @Nonnull final MetaData metaData) {
        if (option != null && StringUtils.isBlank(option.getTopic())) {
            WorkflowTaskTemplate template = option.getTemplate();
            if (template != null) {
                WorkflowTaskInstance added = task.getService().addTask(context, null,
                        task.getPath(), template.getPath(), comment, null, metaData);
                if (added == null) {
                    return new Result(Status.failure);
                }
            }
        }
        return Result.OK;
    }
}
