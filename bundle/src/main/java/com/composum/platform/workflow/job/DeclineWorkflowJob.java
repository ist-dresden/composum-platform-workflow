package com.composum.platform.workflow.job;

import com.composum.platform.models.simple.MetaData;
import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.platform.workflow.service.WorkflowService;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.composum.platform.workflow.job.DeclineWorkflowJob.TOPIC_DECLINE;

@Component(
        property = {
                Constants.SERVICE_DESCRIPTION + "=Generic Workflow Job",
                JobExecutor.PROPERTY_TOPICS + "=" + TOPIC_DECLINE
        }
)
public class DeclineWorkflowJob extends AbstractWorkflowJob {

    private static final Logger LOG = LoggerFactory.getLogger(DeclineWorkflowJob.class);

    public static final String TOPIC_DECLINE = "platform.workflow.decline";

    @Reference
    protected WorkflowService workflowService;

    @Override
    protected WorkflowService getWorkflowService() {
        return workflowService;
    }

    @Override
    public JobResult processTask(@Nonnull final Job job,
                                 @Nonnull final WorkflowTaskInstance task,
                                 @Nonnull final WorkflowTaskTemplate template,
                                 @Nullable final String optionKey, @Nullable final String comment,
                                 @Nonnull final MetaData metaData) {
        String answer = task.getData().get("answer", "");
        if (StringUtils.isNotBlank(answer)) {
            // TODO send email
        }
        return JobResult.OK;
    }
}
