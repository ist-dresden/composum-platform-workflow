<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<head>
    <meta name="viewport" content="width=device-width, minimum-scale=1, maximum-scale=1, user-scalable=no"/>
    <meta name="format-detection" content="telephone=no">
    <title>${cpn:i18n(slingRequest, 'Composum Platform Workflow View')}</title>
    <cpn:clientlib type="link" category="composum.platform.workflow.page"/>
    <cpn:clientlib type="css" category="composum.platform.workflow.page"/>
</head>
