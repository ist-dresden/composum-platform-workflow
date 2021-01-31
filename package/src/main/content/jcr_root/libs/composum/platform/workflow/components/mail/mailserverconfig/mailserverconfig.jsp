<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="cpp" uri="http://sling.composum.com/cppl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<cpp:defineObjects/>
<!-- Unfortunately we can't use EmailServerConfigModel here, since it's not a Model . -->
<cpp:element var="model" type="com.composum.pages.commons.model.GenericModel">
    Mail server configuration ${cpn:text(model.title)}: <p>${cpn:rich(slingRequest, model.description)}</p>
    Type: ${cpn:text(model.valueMap.connectionType)}, host: ${cpn:text(model.resource.valueMap.host)}, port: ${cpn:text(model.resource.valueMap.port)},
    credentialId: ${cpn:text(model.resource.valueMap.credentialId)}.
</cpp:element>
