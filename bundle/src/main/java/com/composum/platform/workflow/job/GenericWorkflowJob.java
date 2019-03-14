package com.composum.platform.workflow.job;

import com.composum.platform.models.simple.MetaData;
import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.platform.workflow.service.WorkflowService;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.composum.platform.workflow.job.GenericWorkflowJob.TOPIC_GENERIC;

@Component(
        property = {
                Constants.SERVICE_DESCRIPTION + "=Generic Workflow Job",
                JobExecutor.PROPERTY_TOPICS + "=" + TOPIC_GENERIC
        }
)
public class GenericWorkflowJob extends AbstractWorkflowJob {

    private static final Logger LOG = LoggerFactory.getLogger(GenericWorkflowJob.class);

    public static final String TOPIC_GENERIC = "platform.workflow.generic";

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
        WorkflowTaskTemplate.Option option = template.getOption(optionKey);
        if (option != null) {
            WorkflowTaskTemplate nextTemplate = option.getTemplate();
            if (nextTemplate != null) {
                workflowService.addTask(null, null, task.getPath(), nextTemplate.getPath(), null, null, metaData);
            }
            return JobResult.OK;
        } else {
            LOG.error("unexpected option '{}' in task '{}'", optionKey, task.getPath());
            return JobResult.CANCEL;
        }
    }
}
