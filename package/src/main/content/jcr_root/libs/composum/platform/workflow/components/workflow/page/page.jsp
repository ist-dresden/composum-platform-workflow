<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<html data-context-path="${slingRequest.contextPath}">
<sling:call script="head.jsp"/>
<sling:call script="body.jsp"/>
</html>
