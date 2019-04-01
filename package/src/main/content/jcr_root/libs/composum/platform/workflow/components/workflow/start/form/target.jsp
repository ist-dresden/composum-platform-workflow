<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<div class="widget multi-form-widget form-group" data-name="wf.target">
    <label class="widget-label"><span
            class="label-text">${cpn:i18n(slingRequest,'Target')}</span><cpn:text
            tagName="span" class="widget-hint" i18n="true" type="rich"
            value="the set of target resources"/></label>
    <div class="multi-form-content">
        <div class="multi-form-item">
            <div class="path input-group widget path-widget">
                <input name="wf.target" class="form-control" type="text"/>
                <span class="input-group-btn">
                  <button class="select btn btn-default" type="button"
                          title="${cpn:i18n(slingRequest,'Select Repository Path')}">...</button>
                </span>
            </div>
        </div>
    </div>
</div>
