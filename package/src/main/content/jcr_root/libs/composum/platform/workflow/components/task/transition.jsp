<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component id="transition" type="com.composum.platform.workflow.model.WorkflowTransition">
    <div class="composum-platform-workflow_graph_transition">
        <div class="graph-transition-bg-left"></div>
        <div class="graph-transition-bg-right"></div>
        <div class="graph-transition-fg">
            <div class="composum-platform-workflow_task_option${transition.current?' current-task':''}${transition.chosen?' chosen-option':''}${transition.workflowEnd?' workflow-end':''}">
                <div class="option-node">
                    <div class="title">${cpn:text(transition.title)}</div>
                    <div class="hint">${cpn:rich(slingRequest,transition.hint)}</div>
                    <cpn:div test="${not empty transition.topic}" class="topic"><span
                            class="icon fa fa-play-circle-o"></span>${cpn:text(transition.topic)}</cpn:div>
                </div>
            </div>
            <cpn:div test="${not transition.workflowEnd}"
                     class="composum-platform-workflow_transition-to"><sling:include
                    path="${transition.toTaskPath}" replaceSelectors="graph" replaceSuffix=""/></cpn:div>
        </div>
    </div>
</cpn:component>
