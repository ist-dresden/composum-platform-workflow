<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component id="workflow" type="com.composum.platform.workflow.model.Workflow" scope="request">
    <div class="composum-platform-workflow_workflow">
        <cpn:div test="${not workflow.hollow}" class="composum-platform-workflow_workflow-graph">
            <sling:include path="${workflow.firstTask.path}" replaceSelectors="graph"/>
        </cpn:div>
    </div>
</cpn:component>
