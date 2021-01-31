package com.composum.platform.workflow.service.impl;

import com.composum.platform.workflow.WorkflowAction;
import com.composum.platform.workflow.service.WorkflowActionManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.osgi.framework.Constants.OBJECTCLASS;

/**
 * the action manager is registering action implementations by their topics
 */
@Component(
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Platform Workflow Action Manager"
        },
        immediate = true
)
public class PlatformActionManager implements WorkflowActionManager {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformActionManager.class);

    protected BundleContext bundleContext;

    protected Map<String, List<ActionReference>> workflowActions = Collections.synchronizedMap(new HashMap<>());

    @Activate
    protected void activate(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    /**
     * @return all registered action implementations for the topic in the service ranking order
     */
    @Override
    @Nullable
    public List<ActionReference> getWorkflowAction(@Nonnull final String topic) {
        return workflowActions.get(topic);
    }

    /**
     * @return all registered action implementations for the type (the class name is used as the topic)
     */
    @Override
    @Nullable
    public List<ActionReference> getWorkflowAction(@Nonnull final Class<? extends WorkflowAction> type) {
        return getWorkflowAction(type.getName());
    }

    @Reference(
            service = WorkflowAction.class,
            policy = ReferencePolicy.DYNAMIC,
            cardinality = ReferenceCardinality.MULTIPLE
    )
    protected void bindWorkflowAction(@Nonnull final ServiceReference<WorkflowAction> actionReference) {
        final ActionReference reference = new PlatformActionReference(actionReference);
        final String[] topics = getTopics(actionReference);
        LOG.info("bindWorkflowAction: {}", StringUtils.join(topics, ","));
        for (String topic : topics) {
            List<ActionReference> topicActions = workflowActions.computeIfAbsent(topic, k -> new ArrayList<>());
            if (!topicActions.contains(reference)) {
                topicActions.add(reference);
                Collections.sort(topicActions);
            }
        }
    }

    protected void unbindWorkflowAction(@Nonnull final ServiceReference<WorkflowAction> actionReference) {
        final ActionReference reference = new PlatformActionReference(actionReference);
        final String[] topics = getTopics(actionReference);
        LOG.info("unbindWorkflowAction: {}", StringUtils.join(topics, ","));
        for (String topic : topics) {
            List<ActionReference> topicActions = workflowActions.get(topic);
            if (topicActions != null) {
                topicActions.remove(reference);
                if (topicActions.size() == 0) {
                    workflowActions.remove(topic);
                }
            }
        }
    }

    protected String[] getTopics(@Nonnull final ServiceReference<WorkflowAction> actionReference) {
        String[] topics = PropertiesUtil.toStringArray(actionReference.getProperty(JobConsumer.PROPERTY_TOPICS));
        if (topics == null || topics.length < 1) {
            topics = (String[]) actionReference.getProperty(OBJECTCLASS);
        }
        return topics;
    }

    protected class PlatformActionReference implements ActionReference {

        public final ServiceReference<WorkflowAction> actionReference;
        public final long serviceId;
        public final int ranking;

        private transient WorkflowAction action;

        public PlatformActionReference(ServiceReference<WorkflowAction> actionReference) {
            this.actionReference = actionReference;
            this.serviceId = (Long) actionReference.getProperty(Constants.SERVICE_ID);
            final Object property = actionReference.getProperty(Constants.SERVICE_RANKING);
            this.ranking = !(property instanceof Integer) ? 0 : (Integer) property;
        }

        @Override
        public WorkflowAction getAction() {
            if (action == null) {
                action = bundleContext.getService(actionReference);
            }
            return action;
        }

        @Override
        public long getServiceId() {
            return serviceId;
        }

        @Override
        public int getRanking() {
            return ranking;
        }

        @Override
        public int compareTo(@Nonnull final ActionReference other) {
            CompareToBuilder builder = new CompareToBuilder();
            builder.append(getRanking(), other.getRanking());
            builder.append(getServiceId(), other.getServiceId());
            return builder.toComparison();
        }

        // Object

        @Override
        public boolean equals(Object other) {
            return other instanceof ActionReference && ((ActionReference) other).getServiceId() == getServiceId();
        }

        @Override
        public int hashCode() {
            return actionReference.hashCode();
        }
    }
}
