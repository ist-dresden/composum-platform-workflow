package com.composum.platform.workflow.mail.impl;

import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;


@Component(
        service = {Runnable.class, EmailCleanupService.class},
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Workflow Email Cleanup Service",
                "scheduler.expression=0 * * * * ?", // FIXME(hps,17.09.20) increase schedule after testing done
                "scheduler.threadPool=" + EmailServiceImpl.THREADPOOL_NAME,
                "scheduler.runOn=SINGLE"
        }
)
@Designate(ocd = EmailCleanupServiceImpl.Config.class)
public class EmailCleanupServiceImpl implements Runnable, EmailCleanupService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailCleanupServiceImpl.class);

    protected volatile Config config;

    @Override
    public void run() {
        LOG.info("running cleanup");
    }

    @Activate
    @Modified
    protected void activate(@Nonnull Config theConfig) {
        this.config = theConfig;
        LOG.info("activated");
    }

    @Deactivate
    protected void deactivate() {
        LOG.info("deactivated");
    }

    @Override
    public boolean keepFailedEmails() {
        return config.keepFailedSeconds() > 0;
    }

    @Override
    public boolean keepDeliveredEmails() {
        return config.keepSuccessfulSeconds() > 0;
    }

    @ObjectClassDefinition(
            name = "Composum Workflow Email Cleanup Service"
    )
    @interface Config {

        @AttributeDefinition(name = "Keep failed time",
                description = "Number of seconds to keep emails that failed emails around. " +
                        "If 0 we delete them immediately.")
        int keepFailedSeconds() default 86400 * 7; // 1 week

        @AttributeDefinition(name = "Keep successful time",
                description = "Number of seconds to keep emails that have been successfully around." +
                        "If 0 we delete them immediately.")
        int keepSuccessfulSeconds() default 86400 * 7; // 1 week

    }

}
