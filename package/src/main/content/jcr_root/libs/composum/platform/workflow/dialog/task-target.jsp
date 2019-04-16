<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<sling:defineObjects/>
<cpn:component id="task" type="com.composum.platform.workflow.model.WorkflowTaskInstance">
    <cpn:div tagName="ul" test="${not empty task.target}" class="composum-platform-workflow_target-list">
        <c:forEach items="${task.target}" var="target">
            <li class="composum-platform-workflow_task-target"><cpn:link href="${target}"
                                                                         format="/bin/pages.html{}">${target}</cpn:link></li>
        </c:forEach>
    </cpn:div>
</cpn:component>
