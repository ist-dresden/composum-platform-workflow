package com.composum.platform.workflow;

import com.composum.platform.models.simple.MetaData;
import com.composum.platform.workflow.model.WorkflowTaskInstance;
import com.composum.platform.workflow.model.WorkflowTaskTemplate;
import com.composum.sling.core.BeanContext;
import org.apache.sling.event.jobs.consumer.JobExecutor;
import org.slf4j.helpers.MessageFormatter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * the interface of a workflow process action - a service which is providing the behaviour of a workflow task
 */
public interface WorkflowAction {

    String PROPERTY_TOPICS = JobExecutor.PROPERTY_TOPICS;

    enum Status {
        success /* successful execution */,
        failure /* execution with errors; suspend the action */,
        cancel  /* execution with errors; discard the action */
    }

    enum Level {info, warning, error}

    class Message {

        public final Level level;
        public final String text;
        public final Object[] args;

        public Message(@Nonnull final Level level, @Nonnull final String text, final Object... args) {
            this.level = level;
            this.text = text;
            this.args = args;
        }

        public String toString() {
            return MessageFormatter.arrayFormat(text, args).getMessage();
        }
    }

    class Result {

        public static final Result OK = new Result();

        protected Status status;
        protected List<Message> messages = new ArrayList<>();

        public Result() {
            this(Status.success);
        }

        public Result(@Nonnull final Status status) {
            setStatus(status);
        }

        public Result(@Nonnull final Status status, Message... messages) {
            this(status);
            this.messages.addAll(Arrays.asList(messages));
        }

        public void merge(Result result) {
            if (result.getStatus() == Status.cancel) {
                setStatus(Status.cancel);
            } else if (result.getStatus() == Status.failure && getStatus() == Status.success) {
                setStatus(Status.failure);
            }
            messages.addAll(result.messages);
        }

        @Nonnull
        public Status getStatus() {
            return status;
        }

        public void setStatus(@Nonnull final Status status) {
            this.status = status;
        }

        public void add(@Nonnull final Message message) {
            messages.add(message);
            if (message.level == Level.error && status == Status.success) {
                status = Status.failure;
            }
        }

        @Nonnull
        public Iterable<Message> getMessages() {
            return messages;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder(getStatus().name());
            if (messages.size() > 0) {
                builder.append("[");
                for (int i = 0; i < messages.size(); ) {
                    Message message = messages.get(i);
                    builder.append(message.level).append(":").append(message.toString());
                    if (++i < messages.size()) {
                        builder.append(",");
                    }
                }
                builder.append("]");
            }
            return builder.toString();
        }
    }

    /**
     * executes the action of a task with the chosen option
     *
     * @param context  the current request context (user session)
     * @param task     the task to process
     * @param option   the chosen option
     * @param comment  an optional comment
     * @param metaData a set of probably useful properties
     */
    @Nonnull
    Result process(@Nonnull final BeanContext context,
                   @Nonnull final WorkflowTaskInstance task,
                   @Nullable final WorkflowTaskTemplate.Option option, @Nullable final String comment,
                   @Nonnull final MetaData metaData)
            throws Exception;
}
