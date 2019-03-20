<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component id="task" type="com.composum.platform.workflow.model.WorkflowTaskTemplate">
    <div class="composum-platform-workflow_task task-template${task.autoRun?' auto-run':''}${task.current?' current-task':''}${task.workflowStart?' workflow-start':''}${task.workflowEnd?' workflow-end':''}"
         data-path="${task.path}">
        <div class="task-node">
            <div class="meta">
                <cpn:div test="${not empty task.assignee}" class="assignee"><span
                        class="icon fa fa-user-o"></span>${cpn:text(task.assignee)}</cpn:div>
            </div>
            <div class="title"><span
                    class="icon fa fa-${task.autoRun?'cog':'check-square-o'}"></span>${cpn:text(task.title)}
            </div>
            <div class="hint">${cpn:rich(slingRequest,task.hint)}</div>
            <c:if test="${not empty task.dialog}">
                <div class="data"><sling:include resourceType="${task.dialog}" replaceSelectors="task-data"/></div>
            </c:if>
            <div class="topic"><span class="icon fa fa-play-circle-o"></span>${cpn:path(task.topic)}</div>
            <div class="template"><span class="icon fa fa-wrench"></span>${cpn:path(task.path)}</div>
        </div>
    </div>
</cpn:component>
