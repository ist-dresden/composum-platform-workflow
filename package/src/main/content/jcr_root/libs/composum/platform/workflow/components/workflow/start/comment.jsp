<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<div class="form-group">
    <label class="widget-label"><span
            class="label-text">${cpn:i18n(slingRequest,'Comment')}</span><cpn:text
            tagName="span" class="widget-hint" i18n="true" type="rich"
            value="an internal comment (optional)"/></label>
    <textarea name="wf.comment" class="widget text-area-widget form-control"></textarea>
</div>
