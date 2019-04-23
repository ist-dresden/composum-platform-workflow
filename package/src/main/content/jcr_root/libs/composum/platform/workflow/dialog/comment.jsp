<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<div class="form-group widget text-area-widget">
    <label class="widget-label"><span
            class="label-text">${cpn:i18n(slingRequest,'Comment')}</span><cpn:text
            tagName="span" class="widget-hint"
            i18n="true" value="an internal comment (optional)" type="rich"/></label>
    <textarea name="wf.comment" class="form-control"></textarea>
</div>
