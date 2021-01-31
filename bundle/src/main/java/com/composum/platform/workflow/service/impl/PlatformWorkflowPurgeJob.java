package com.composum.platform.workflow.service.impl;

import com.composum.platform.workflow.service.WorkflowPurgeJob;
import com.composum.platform.workflow.service.WorkflowService;
import com.composum.sling.core.BeanContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobBuilder;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.ScheduledJobInfo;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

import static com.composum.platform.workflow.service.impl.PlatformWorkflowPurgeJob.PURGE_JOB_TOPIC;

/**
 * a Job implementation to schedule the purge of finished workflows
 */
@Component(
        service = {JobConsumer.class, WorkflowPurgeJob.class},
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Platform Workflow Purge Job",
                JobExecutor.PROPERTY_TOPICS + "=" + PURGE_JOB_TOPIC
        },
        immediate = true
)
public class PlatformWorkflowPurgeJob implements WorkflowPurgeJob {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformWorkflowPurgeJob.class);

    public static final String PURGE_JOB_TOPIC = "composum/platform/workflow/purge";

    @Reference
    protected ResourceResolverFactory resolverFactory;

    @Reference
    protected JobManager jobManager;

    @Reference
    protected WorkflowService workflowService;

    protected WorkflowService.Configuration config;

    @Activate
    @Modified
    public void activate(BundleContext bundleContext) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("activate...");
        }
        stopScheduledJob();
        this.config = workflowService.getConfig();
        startScheduledJob();
    }

    @Deactivate
    public void deactivate(BundleContext bundleContext) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("deactivate...");
        }
        stopScheduledJob();
    }

    @Override
    public void startScheduledJob() {
        if (config != null && StringUtils.isNotBlank(config.purge_job_cron())) {
            JobBuilder.ScheduleBuilder scheduleBuilder = jobManager.createJob(PURGE_JOB_TOPIC).schedule();
            scheduleBuilder.cron(config.purge_job_cron()).add();
            if (LOG.isInfoEnabled()) {
                LOG.info("start scheduled workflow purge job ({})", config.purge_job_cron());
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void stopScheduledJob() {
        Collection<ScheduledJobInfo> jobs = jobManager.getScheduledJobs(PURGE_JOB_TOPIC, 0);
        for (ScheduledJobInfo job : jobs) {
            if (LOG.isInfoEnabled()) {
                LOG.info("stop scheduled workflow purge job");
            }
            job.unschedule();
        }
    }

    @Override
    public JobResult process(Job job) {
        if (config != null) {
            try (final ResourceResolver serviceResolver = resolverFactory.getServiceResourceResolver(null)) {
                BeanContext serviceContext = new BeanContext.Service(serviceResolver);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Start of workflow purge job execution...");
                }
                workflowService.purgeTasks(serviceContext, config.workflow_keep_days());
                if (LOG.isInfoEnabled()) {
                    LOG.info("Workflow purge job execution done.");
                }
            } catch (LoginException ex) {
                LOG.error(ex.toString());
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
        return JobResult.OK;
    }
}
