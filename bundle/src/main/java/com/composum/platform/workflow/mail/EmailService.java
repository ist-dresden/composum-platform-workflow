package com.composum.platform.workflow.mail;

import javax.annotation.Nullable;

/**
 * Allows sending emails, adress verification etc.
 */
public interface EmailService {

    /**
     * Checks whether this is a valid email address to send to.
     */
    boolean isValid(@Nullable String emailAdress);

}
