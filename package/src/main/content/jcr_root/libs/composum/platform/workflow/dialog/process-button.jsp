<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<button type="submit"
        class="workflow-dialog_button-submit workflow-dialog_button btn btn-primary">${cpn:i18n(slingRequest,'Process')}</button>
