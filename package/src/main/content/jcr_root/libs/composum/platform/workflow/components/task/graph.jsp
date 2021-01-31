<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<sling:defineObjects/>
<div class="composum-platform-workflow_graph_task">
    <div class="graph-task-bg-left"></div>
    <div class="graph-task-bg-right"></div>
    <div class="graph-task-fg">
        <sling:call script="task.jsp"/>
    </div>
</div>
<sling:call script="transitions.jsp"/>
