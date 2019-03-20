<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<body class="composum-platform-workflow_body">
<sling:call script="content.jsp"/>
<cpn:clientlib type="js" category="composum.platform.workflow"/>
</body>
