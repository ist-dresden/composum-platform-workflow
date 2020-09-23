<%@ page session="true" pageEncoding="UTF-8" %>
<%-- entry: http://localhost:9090/apps/composum/platform/workflow/test/mail/mailform.html --%>
<%@ page import="com.composum.platform.workflow.mail.EmailBuilder" %>
<%@ page import="com.composum.sling.core.BeanContext" %>
<%@ page import="java.util.concurrent.Future" %>
<%@ page import="com.composum.platform.workflow.mail.EmailService" %>
<%@ page import="org.apache.sling.api.resource.Resource" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling" %>
<%@ taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<%
    String serverConfig = slingRequest.getParameter("serverConfig");
    Resource serverConfigResource = resourceResolver.getResource(serverConfig);
    if (serverConfigResource == null) {
        throw new IllegalArgumentException("Server configuration not found at " + serverConfig);
    }

    EmailBuilder email = new EmailBuilder(new BeanContext.Page(pageContext), null);
    email.setFrom(slingRequest.getParameter("from"));
    email.setTo(slingRequest.getParameter("to"));
    email.setCC(slingRequest.getParameter("cc"));
    email.setBCC(slingRequest.getParameter("bcc"));
    email.setReplyTo(slingRequest.getParameter("replyTo"));
    email.setBounceAddress(slingRequest.getParameter("bounceAddress"));
    email.setSubject(slingRequest.getParameter("subject"));
    email.setHTML(slingRequest.getParameter("html"));
    email.setBody(slingRequest.getParameter("text"));

    EmailService service = sling.getService(EmailService.class);

    Future<String> result = service.sendMail(null, email, serverConfigResource);
    slingRequest.getSession().setAttribute("email-future", result);

    slingResponse.sendRedirect(request.getRequestURI());
    slingResponse.setStatus(HttpServletResponse.SC_SEE_OTHER);
%>
