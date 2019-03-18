package com.composum.platform.workflow.service;

import org.apache.sling.event.jobs.consumer.JobConsumer;

public interface WorkflowPurgeJob extends JobConsumer {

    void startScheduledJob();

    void stopScheduledJob();
}
