package com.composum.platform.workflow.mail.impl;

/** Business interface of {@link EmailCleanupServiceImpl}, mainly for communication with {@link EmailServiceImpl}. */
public interface EmailCleanupService {

    /** Whether the {@link com.composum.platform.workflow.mail.EmailService} should keep failed emails or delete them. */
    boolean keepFailedEmails();

    /** Whether the {@link com.composum.platform.workflow.mail.EmailService} should keep delivered emails or delete them. */
    boolean keepDeliveredEmails();

}
