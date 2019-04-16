package com.composum.platform.workflow.action;

import com.composum.platform.workflow.WorkflowAction;
import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.sling.core.BeanContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.regex.Pattern;

import static com.composum.platform.workflow.action.DeclineWorkflowAction.TOPIC_DECLINE;

@Component(
        property = {
                Constants.SERVICE_DESCRIPTION + "=Decline Workflow Job",
                WorkflowAction.PROPERTY_TOPICS + "=" + TOPIC_DECLINE
        }
)
public class DeclineWorkflowAction implements WorkflowAction {

    public static final String PN_ANSWER = "answer";

    private static final Logger LOG = LoggerFactory.getLogger(DeclineWorkflowAction.class);

    public static final String TOPIC_DECLINE = "composum/platform/workflow/decline";

    public static final Pattern MAIL_ADDRESS = Pattern.compile(".+@.*[^.]+\\.[^.]+$");

    @Override
    @Nonnull
    public Result process(@Nonnull final BeanContext context, @Nonnull final WorkflowTaskInstance task,
                          @Nullable final WorkflowTaskTemplate.Option option, @Nullable final ValueMap data,
                          @Nullable final String comment) {
        String answer = task.getData().get(PN_ANSWER, "");
        if (LOG.isDebugEnabled()) {
            LOG.debug("process '{}.{}'...", task.getName(), option != null ? option.getName() : "null");
        }
        if (StringUtils.isNotBlank(answer)) {
            String mailTo = task.getData().get("userId", "");
            if (MAIL_ADDRESS.matcher(mailTo).matches()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("sending email to '{}'...", mailTo);
                }
                // TODO send email
            } else {
                LOG.error("invalid mail address '{}'", mailTo);
            }
        }
        return Result.OK;
    }
}
