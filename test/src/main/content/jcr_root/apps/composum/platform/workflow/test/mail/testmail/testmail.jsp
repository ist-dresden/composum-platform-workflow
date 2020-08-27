<%@ page import="com.composum.platform.workflow.mail.EmailBuilder" %>
<%@ page import="com.composum.platform.workflow.mail.EmailService" %>
<%@ page import="com.composum.sling.core.BeanContext" %>
<%@ page import="org.apache.sling.api.resource.Resource" %>
<%@ page import="java.util.concurrent.Future" %>
<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
Mailing now.
<%
    EmailService service = sling.getService(EmailService.class);
    Resource serverConfigResource = resourceResolver.getResource("/var/composum/platform/mail/composum/platform/workflow/test/mail/server/sendgrid");

    EmailBuilder email = new EmailBuilder(new BeanContext.Page(pageContext), null);
    email.setFrom("hps@ist-software.com");
    email.setSubject("TestMail ${X} too");
    email.setBody("This is a test impl ... :-)");
    email.setTo("hps@ist-software.com");
    email.addPlaceholder( "X", "is this");
    Future<String> result = service.sendMail(email, serverConfigResource);
%>
Sent - the result <%= result.get() %>
