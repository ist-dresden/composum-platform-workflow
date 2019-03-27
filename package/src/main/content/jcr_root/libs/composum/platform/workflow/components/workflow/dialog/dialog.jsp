<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<cpn:component id="workflow" type="com.composum.platform.workflow.model.Workflow" scope="request">
    <div class="composum-platform-workflow_dialog workflow-graph dialog modal fade">
        <div class="modal-dialog">
            <sling:call script="content.jsp"/>
        </div>
    </div>
</cpn:component>
