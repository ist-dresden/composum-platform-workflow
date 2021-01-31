<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<sling:defineObjects/>
<div class="workflow-dialog_task-description">
    <sling:call script="task-description.jsp"/>
</div>
<div class="workflow-dialog_task-data">
    <sling:call script="task-data.jsp"/>
</div>

