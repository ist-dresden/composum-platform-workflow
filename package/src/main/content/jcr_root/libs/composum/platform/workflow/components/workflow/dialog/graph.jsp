<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<div class="workflow-dialog_graph-content">
    <sling:include resourceType="composum/platform/workflow/components/workflow" replaceSelectors="graph"/>
</div>
