<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component id="inbox" type="com.composum.platform.workflow.model.WorkflowInboxModel">
    <div class="composum-platform-workflow_inbox-table" data-path="${inbox.path}" data-scope="${inbox.scope}">
        <table class="table table-striped table-hover table-condensed">
            <thead>
            <tr>
                <th class="sel"></th>
                <th class="date">${cpn:i18n(slingRequest,'Date')}</th>
                <th class="title">${cpn:i18n(slingRequest,'Task')}</th>
                <th class="hint">${cpn:i18n(slingRequest,'Description')}</th>
                <th class="assignee">${cpn:i18n(slingRequest,'Assignee')}</th>
            </tr>
            </thead>
            <tbody>
            <c:choose>
                <c:when test="${not empty inbox.tasks}">
                    <c:forEach items="${inbox.tasks}" var="task">
                        <tr class="composum-platform-workflow_inbox-task item table-row task-state_${task.cancelled?'cancelled':task.state}${task.autoRun?' auto-run':''}"
                            data-path="${task.path}" data-state="${task.state}" data-graph="${task.graphAvailable}"
                            data-cancel="${task.cancellingAllowed}">
                            <td class="sel"><label><input type="radio" name="task" value="${task.path}"/></label></td>
                            <td class="date">${task.date}</td>
                            <td class="title">${cpn:text(task.title)}</td>
                            <td class="hint">${cpn:rich(slingRequest,task.hint)}</td>
                            <td class="assignee">${cpn:text(task.assignee)}</td>
                        </tr>
                    </c:forEach>
                </c:when>
                <c:otherwise>
                    <tr>
                        <td colspan="4">${cpn:i18n(slingRequest,'the inbox is empty')}</td>
                    </tr>
                </c:otherwise>
            </c:choose>
            </tbody>
        </table>
    </div>
</cpn:component>
