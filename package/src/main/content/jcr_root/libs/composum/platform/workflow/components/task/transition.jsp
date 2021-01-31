<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component id="workflow_transition" type="com.composum.platform.workflow.model.WorkflowTransition" scope="request"
               replace="true">
    <div class="composum-platform-workflow_graph_transition">
        <div class="graph-transition-bg-left"></div>
        <div class="graph-transition-bg-right"></div>
        <div class="graph-transition-fg">
            <div class="composum-platform-workflow_task_option${workflow_transition.current?' current-task':''}${workflow_transition.chosen?' chosen-option':''}${workflow_transition.workflowEnd?' workflow-end':''}">
                <div class="option-node">
                    <div class="task-title">${cpn:text(workflow_transition.title)}</div>
                    <div class="task-hint">${cpn:rich(slingRequest,workflow_transition.hint)}</div>
                    <cpn:div test="${not empty workflow_transition.topic}" class="task-topic"><span
                            class="icon fa fa-play-circle-o"></span>${cpn:text(workflow_transition.topic)}</cpn:div>
                </div>
            </div>
            <cpn:div test="${not workflow_transition.workflowEnd}"
                     class="composum-platform-workflow_transition-to"><sling:include
                    path="${workflow_transition.toTaskPath}" replaceSelectors="graph" replaceSuffix=""/></cpn:div>
        </div>
    </div>
</cpn:component>
