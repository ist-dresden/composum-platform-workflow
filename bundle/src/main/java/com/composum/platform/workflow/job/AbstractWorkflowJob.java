package com.composum.platform.workflow.job;

import com.composum.platform.models.simple.MetaData;
import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.platform.workflow.service.WorkflowService;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static com.composum.platform.workflow.service.WorkflowService.META_OPTION;
import static com.composum.platform.workflow.service.WorkflowService.META_USER_ID;
import static com.composum.platform.workflow.service.WorkflowService.PN_TASK_INITIATOR;
import static com.composum.platform.workflow.service.impl.PlatformWorkflowService.PN_TASK_INSTANCE_PATH;
import static com.composum.platform.workflow.service.impl.PlatformWorkflowService.PN_TASK_OPTION;

public abstract class AbstractWorkflowJob implements JobConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractWorkflowJob.class);

    protected abstract WorkflowService getWorkflowService();

    abstract JobResult processTask(@Nonnull final Job job,
                                   @Nonnull final WorkflowTaskInstance task,
                                   @Nonnull final WorkflowTaskTemplate template,
                                   @Nullable final String option, @Nullable final String comment,
                                   @Nonnull final MetaData metaData)
            throws Exception;

    @Override
    public JobResult process(Job job) {
        JobResult result = JobResult.CANCEL;
        MetaData metaData = new MetaData();
        String taskPath = (String) job.getProperty(PN_TASK_INSTANCE_PATH);
        String option = (String) job.getProperty(PN_TASK_OPTION);
        metaData.put(META_OPTION, option);
        metaData.put(META_USER_ID, job.getProperty(PN_TASK_INITIATOR));
        WorkflowTaskInstance taskInstance = getWorkflowService().getInstance(null, taskPath);
        if (taskInstance != null) {
            try {
                WorkflowTaskTemplate taskTemplate = taskInstance.getTemplate();
                if (taskTemplate != null) {
                    result = processTask(job, taskInstance, taskTemplate, option, null, metaData);
                } else {
                    LOG.error("no template available for: '{}'", taskPath);
                }
                taskInstance = getWorkflowService().getInstance(null, taskPath);
                if (taskInstance != null && taskInstance.getState() != WorkflowTaskInstance.State.finished) {
                    getWorkflowService().finishTask(null, taskPath, false, null, null, metaData);
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        } else {
            LOG.error("running task not available: '{}'", taskPath);
        }
        return result;
    }
}
