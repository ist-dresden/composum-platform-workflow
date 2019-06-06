package com.composum.platform.workflow.service;

import com.composum.platform.workflow.WorkflowAction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface WorkflowActionManager {

    interface ActionReference extends Comparable<ActionReference> {

        long getServiceId();

        int getRanking();

        WorkflowAction getAction();
    }

    @Nullable
    List<ActionReference> getWorkflowAction(@Nonnull String topic);

    @Nullable
    List<ActionReference> getWorkflowAction(@Nonnull Class<? extends WorkflowAction> type);
}

