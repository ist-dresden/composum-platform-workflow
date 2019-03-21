<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component id="inbox" type="com.composum.platform.workflow.model.WorkflowInboxModel">
    <div class="composum-platform-workflow_inbox-table" data-path="${inbox.path}">
        <table class="table table-striped table-hover table-condensed">
            <thead>
            <tr>
                <th class="date">Date</th>
                <th class="title">Task</th>
                <th class="hint">Description</th>
                <th class="assignee">Assignee</th>
                <th class="workflow"><span class="fa fa-search"></span></th>
            </tr>
            </thead>
            <tbody>
            <c:choose>
                <c:when test="${not empty inbox.tasks}">
                    <c:forEach items="${inbox.tasks}" var="task">
                        <tr class="composum-platform-workflow_inbox-task item table-row" data-path="${task.path}">
                            <td class="date">${task.date}</td>
                            <td class="title"
                                data-hint="${cpn:attr(slingRequest,task.dataHint,0)}">${cpn:text(task.title)}</td>
                            <td class="hint">${cpn:rich(slingRequest,task.hint)}</td>
                            <td class="assignee">${cpn:text(task.assignee)}</td>
                            <td class="workflow">
                                <span class="show-workflow fa fa-sitemap"
                                      title="${cpn:i18n(slingRequest,'Workflow')}"></span>
                            </td>
                        </tr>
                    </c:forEach>
                </c:when>
                <c:otherwise>
                    <tr>
                        <td colspan="4">the inbox is empty</td>
                    </tr>
                </c:otherwise>
            </c:choose>
            </tbody>
        </table>
    </div>
</cpn:component>
