<%@ page import="com.composum.platform.workflow.mail.EmailService" %>
<%@ page import="org.apache.commons.mail.Email" %>
<%@ page import="org.apache.commons.mail.SimpleEmail" %>
<%@ page import="org.apache.sling.api.resource.Resource" %>
<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
Mailing now.
<%
    EmailService service = sling.getService(EmailService.class);
    Resource serverConfigResource = resourceResolver.getResource("/var/composum/platform/mail/composum/platform/workflow/test/mail/server/sendgrid");

     Email email = new SimpleEmail();
     email.setFrom("hps@ist-software.com");
     email.setSubject("TestMail too");
     email.setMsg("This is a test impl ... :-)");
     email.addTo("hps@ist-software.com");
     String result = service.sendMail(email, serverConfigResource);
%>
Sent - result <%= result%>
