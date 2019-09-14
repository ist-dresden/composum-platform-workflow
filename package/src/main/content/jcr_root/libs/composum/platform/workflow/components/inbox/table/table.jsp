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
                <th class="task-sel"></th>
                <th class="task-date">${cpn:i18n(slingRequest,'Date')}</th>
                <th class="task-title">${cpn:i18n(slingRequest,'Task')}</th>
                <th class="task-hint">${cpn:i18n(slingRequest,'Description')}</th>
                <th class="task-assignee">${cpn:i18n(slingRequest,'Assignee')}</th>
            </tr>
            </thead>
            <tbody>
            <c:choose>
                <c:when test="${not empty inbox.tasks}">
                    <c:forEach items="${inbox.tasks}" var="task">
                        <tr class="composum-platform-workflow_inbox-task item table-row task-state_${task.cancelled?'cancelled':task.state}${task.autoRun?' auto-run':''}"
                            data-path="${task.path}" data-state="${task.state}" data-graph="${task.graphAvailable}"
                            data-cancel="${task.cancellingAllowed}">
                            <td class="task-sel"><label><input type="radio" name="task" value="${task.path}"/></label></td>
                            <td class="task-date">${task.date}</td>
                            <td class="task-title">${cpn:text(task.title)}</td>
                            <td class="task-hint">${cpn:rich(slingRequest,task.hint)}</td>
                            <td class="task-assignee">${cpn:text(task.assignee)}</td>
                        </tr>
                    </c:forEach>
                </c:when>
                <c:otherwise>
                    <tr>
                        <td colspan="5" style="padding: 0; background-color: #fff;"><sling:include
                                resourceType="composum/platform/workflow/components/inbox/empty"/></td>
                    </tr>
                </c:otherwise>
            </c:choose>
            </tbody>
        </table>
    </div>
</cpn:component>
