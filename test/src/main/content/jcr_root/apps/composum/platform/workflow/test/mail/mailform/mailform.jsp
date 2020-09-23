<%@ page import="com.composum.sling.core.util.ResourceUtil" %>
<%@ page import="com.composum.sling.platform.staging.query.Query" %>
<%@ page import="com.composum.sling.platform.staging.query.QueryBuilder" %>
<%@ page import="org.apache.sling.api.resource.Resource" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.concurrent.Future" %>
<%@ page import="java.util.concurrent.TimeUnit" %>
<%@ page import="java.util.concurrent.TimeoutException" %>
<%@ page import="java.util.stream.Collectors" %>
<%@page session="true" pageEncoding="UTF-8" %>
<%-- http://localhost:9090/apps/composum/platform/workflow/test/mail/mailform.html --%>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<cpn:clientlib type="css" category="composum.nodes.console.default"/>
<%
    Future<String> sent = (Future<String>) slingRequest.getSession().getAttribute("email-future");
    String messageId = null;
    boolean notDone = false;
    String error = null;
    if (sent != null) {
        try {
            messageId = sent.get(2, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // OK, still in sending process
            notDone = true;
        } catch (Exception e) {
            error = e.toString();
        }
    }
    notDone = notDone || (sent != null && !sent.isDone());
    if (sent != null && sent.isDone()) {
        slingRequest.getSession().removeAttribute("email-future"); // just display once.
    }

    Query query = resourceResolver.adaptTo(QueryBuilder.class).createQuery();
    query.path("/").condition(
            query.conditionBuilder().group(
                    (g) -> g.isDescendantOf("/conf").or().isDescendantOf("/var").or().isDescendantOf("/content").or().isDescendantOf("/etc")
            ).and().property(ResourceUtil.PROP_RESOURCE_TYPE).eq().val("composum/platform/workflow/components/mail/mailserverconfig")
    );
    List<String> serverConfigs = query.stream().map(Resource::getPath).collect(Collectors.toList());
%>
<div class="modal-content form-panel default">
    <%-- for debugging: action = /apps/ist/composum/testpages/debug.misc.html --%>
    <form action="/apps/composum/platform/workflow/test/mail/mailform.html" method="POST" class="widget-form default"
          enctype="multipart/form-data" accept-charset="utf-8">

        <div class="modal-header">
            <h4 class="modal-title">Send mail</h4>
        </div>

        <div class="modal-body">
            <div class="messages">
                <% if (messageId != null) { %>
                <div class="alert alert-success">Successful sent with message ID <%= messageId %>
                </div>
                <% } %>
                <% if (notDone) { %>
                <div class="alert alert-warning">Still sending email</div>
                <% } %>
                <% if (error != null) { %>
                <div class="alert alert-danger">Error: <%= error %>
                </div>
                <% } %>
            </div>
            <div class="form-group subtype">
                <label class="control-label">Email server configuration (*)</label>
                <select name="serverConfig" class="subtype-select widget select-widget form-control"
                        style="visibility: visible;">
                    <% for (String serverConfig : serverConfigs) { %>
                    <option value=<%= serverConfig %>><%= serverConfig%>
                    </option>
                    <% } %>
                </select>
            </div>
            <div class="form-group">
                <label class="control-label">From (*)</label>
                <input name="from" class="widget property-name-widget form-control" type="text"
                       placeholder="Sender of the email" data-rules="mandatory">
            </div>
            <div class="form-group">
                <label class="control-label">To (*)</label>
                <input name="to" class="widget property-name-widget form-control" type="text"
                       placeholder="Receiver of the email" data-rules="mandatory">
            </div>
            <div class="form-group">
                <label class="control-label">CC</label>
                <input name="cc" class="widget property-name-widget form-control" type="text"
                       placeholder="CC of the email">
            </div>
            <div class="form-group">
                <label class="control-label">BCC</label>
                <input name="bcc" class="widget property-name-widget form-control" type="text"
                       placeholder="BCC of the email">
            </div>
            <div class="form-group">
                <label class="control-label">Reply To</label>
                <input name="replyTo" class="widget property-name-widget form-control" type="text"
                       placeholder="ReplyTo header of the email">
            </div>
            <div class="form-group">
                <label class="control-label">Bounce Address</label>
                <input name="bounceAddress" class="widget property-name-widget form-control" type="text"
                       placeholder="Bounce address of the email">
            </div>
            <div class="form-group">
                <label class="control-label">Subject (*)</label>
                <input name="subject" class="widget property-name-widget form-control" type="text"
                       placeholder="Subject of the email" data-rules="mandatory">
            </div>
            <div class="form-group">
                <label class="control-label">Text</label>
                <textarea name="text"
                          class="plaintext property-type-plaintext widget text-area-widget form-control"
                          rows="4"></textarea>
            </div>
            <div class="form-group">
                <label class="control-label">HTML (at the moment plain HTML, no richtext editor)</label>
                <textarea name="html"
                          class="plaintext property-type-plaintext widget text-area-widget form-control"
                          rows="4"></textarea>
            </div>
        </div>

        <div class="modal-footer buttons">
            <button type="submit" class="btn btn-primary default save">Send</button>
        </div>

    </form>
</div>
