<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<cpn:component id="workflow" type="com.composum.platform.workflow.model.Workflow" scope="request">
    <sling:include resourceType="${workflow.formType}"/>
</cpn:component>
