<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component id="task" type="com.composum.platform.workflow.model.WorkflowTaskInstance">
    <div class="composum-platform-workflow_task task-instance task-state_${task.cancelled?'cancelled':task.state}${task.autoRun?' auto-run':''}${task.current?' current-task':''}${task.workflowStart?' workflow-start':''}${task.workflowEnd?' workflow-end':''}"
         data-path="${task.path}">
        <div class="task-node">
            <div class="task-meta">
                <div class="task-date"><span class="icon fa fa-calendar-o"></span>${task.date}</div>
                <cpn:div test="${not empty task.userId}" class="task-assignee"><span
                        class="icon fa fa-user-o"></span>${cpn:text(task.userId)}</cpn:div>
            </div>
            <div class="task-title"><span
                    class="icon fa fa-${task.autoRun?'cog':'check-square-o'}"></span>${cpn:text(task.title)}
            </div>
            <div class="task-hint">${cpn:rich(slingRequest,task.hint)}</div>
            <c:if test="${not empty task.dialog}">
                <div class="task-data"><sling:include resourceType="${task.dialog}"
                                                 replaceSelectors="task-data"/></div>
            </c:if>
            <cpn:div test="${not empty task.topic}" class="task-topic"><span
                    class="icon fa fa-play-circle-o"></span>${cpn:path(task.topic)}</cpn:div>
            <div class="task-template"><span class="icon fa fa-wrench"></span>${cpn:path(task.template.path)}</div>
        </div>
    </div>
</cpn:component>
