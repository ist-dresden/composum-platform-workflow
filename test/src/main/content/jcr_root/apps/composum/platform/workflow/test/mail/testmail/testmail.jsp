<%@ page import="com.composum.platform.workflow.mail.EmailBuilder" %>
<%@ page import="com.composum.platform.workflow.mail.EmailService" %>
<%@ page import="com.composum.sling.core.BeanContext" %>
<%@ page import="org.apache.sling.api.resource.Resource" %>
<%@ page import="java.util.concurrent.Future" %>
<%@ page import="java.util.concurrent.TimeUnit" %>
<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
Mailing now.
<%
    EmailService service = sling.getService(EmailService.class);
    Resource serverConfigResource = resourceResolver.getResource("/var/composum/platform/mail/composum/platform/workflow/test/mail/server/sendgrid");
    Resource emailTemplateResource = resourceResolver.getResource("/var/composum/platform/mail/composum/platform/workflow/test/mail/templates/helloworld");

    EmailBuilder email = new EmailBuilder(new BeanContext.Page(pageContext), emailTemplateResource);
    // email.setFrom("hps@ist-software.com");
    // email.setSubject("TestMail ${X} too");
    // email.setBody("This is a test impl ... :-)");
    // email.setTo("hps@ist-software.com");
    // email.addPlaceholder( "X", "is this");
    email.addPlaceholder("B", "-BVALUE-");
    email.addPlaceholder("S", "-SVALUE-");
    Future<String> result = service.sendMail(email, serverConfigResource);
%>
Sent template - the result <%= result.get(120000, TimeUnit.SECONDS) %>
