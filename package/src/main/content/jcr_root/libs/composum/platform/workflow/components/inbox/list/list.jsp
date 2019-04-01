<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component id="inbox" type="com.composum.platform.workflow.model.WorkflowInboxModel">
    <div class="composum-platform-workflow_inbox" data-path="${inbox.path}" data-scope="${inbox.scope}"
         data-tenant="${inbox.tenantId}">
        <c:choose>
            <c:when test="${not empty inbox.tasks}">
                <ul class="composum-platform-workflow_inbox-list">
                    <c:forEach items="${inbox.tasks}" var="task">
                        <li class="composum-platform-workflow_inbox-task item list-item task-state_${task.cancelled?'cancelled':task.state}${task.autoRun?' auto-run':''}"
                            data-path="${task.path}" data-state="${task.state}" data-graph="${task.graphAvailable}"
                            data-cancel="${task.cancellingAllowed}">
                            <div class="meta">
                                <div class="date"><span class="icon fa fa-calendar-o"></span>${task.date}</div>
                                <cpn:div test="${not empty task.userId}" class="assignee"><span
                                        class="icon fa fa-user-o"></span>${cpn:text(task.userId)}</cpn:div>
                            </div>
                            <div class="title"><span
                                    class="icon fa fa-${task.autoRun?'cog':'check-square-o'}"></span>${cpn:text(task.title)}
                            </div>
                            <div class="hint">${cpn:rich(slingRequest,task.hint)}</div>
                            <c:if test="${not empty task.dialog}">
                                <div class="data"><sling:include resource="${task.resource}"
                                                                 resourceType="${task.dialog}"
                                                                 replaceSelectors="task-data"/></div>
                            </c:if>
                            <cpn:div test="${not empty task.topic}" class="topic"><span
                                    class="icon fa fa-play-circle-o"></span>${cpn:path(task.topic)}</cpn:div>
                            <div class="template"><span class="icon fa fa-wrench"></span>${cpn:path(task.template.path)}
                            </div>
                        </li>
                    </c:forEach>
                </ul>
            </c:when>
            <c:otherwise>
                <div class="composum-platform-workflow_inbox-empty">${cpn:i18n(slingRequest,'the inbox is empty')}</div>
            </c:otherwise>
        </c:choose>
    </div>
</cpn:component>
