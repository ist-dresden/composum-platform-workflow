<%@page session="false" pageEncoding="utf-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<sling:defineObjects/>
<div class="workflow-inbox-toolbar detail-toolbar">
    <div class="btn-group btn-group-sm" role="group">
        <button class="start fa fa-plus btn btn-default"
                title="${cpn:i18n(slingRequest,'Start a Workflow')}"><cpn:text
                value="${cpn:i18n(slingRequest,'Start')}" tagName="span" class="label" i18n="true"/></button>
    </div>
    <div class="btn-group btn-group-sm" role="group">
        <button class="process fa fa-chevron-right btn btn-default"
                title="${cpn:i18n(slingRequest,'Process the selected Task')}"><cpn:text
                value="${cpn:i18n(slingRequest,'Process')}" tagName="span" class="label" i18n="true"/></button>
        <button class="detail fa fa-search btn btn-default"
                title="${cpn:i18n(slingRequest,'Show workflow Details')}"><cpn:text
                value="${cpn:i18n(slingRequest,'Details')}" tagName="span" class="label" i18n="true"/></button>
        <button class="cancel fa fa-times btn btn-default"
                title="${cpn:i18n(slingRequest,'Cancel the selected Task')}"><cpn:text
                value="${cpn:i18n(slingRequest,'Cancel')}" tagName="span" class="label" i18n="true"/></button>
    </div>
    <div class="scope btn-group btn-group-sm toolbar-input-field" role="group">
        <div class="input-group input-group-sm">
            <select class="form-control" title="${cpn:i18n(slingRequest,'Select table Scope')}">
                <option value="pending">${cpn:i18n(slingRequest,'pending')}</option>
                <option value="running">${cpn:i18n(slingRequest,'running')}</option>
                <option value="finished">${cpn:i18n(slingRequest,'finished')}</option>
            </select>
        </div>
    </div>
    <div class="btn-group btn-group-sm" role="group">
        <button class="reload fa fa-refresh btn btn-default"
                title="${cpn:i18n(slingRequest,'Reload')}"><cpn:text
                value="${cpn:i18n(slingRequest,'Reload')}" tagName="span" class="label" i18n="true"/></button>
    </div>
</div>