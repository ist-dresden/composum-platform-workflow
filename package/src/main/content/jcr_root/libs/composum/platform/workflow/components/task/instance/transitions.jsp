<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component id="task" type="com.composum.platform.workflow.model.WorkflowTaskInstance">
    <cpn:div test="${not empty task.options}" class="composum-platform-workflow_graph_task-options">
        <c:forEach items="${task.options}" var="option">
            <sling:include replaceSelectors="transition" replaceSuffix="/${option.name}"/>
        </c:forEach>
    </cpn:div>
</cpn:component>
