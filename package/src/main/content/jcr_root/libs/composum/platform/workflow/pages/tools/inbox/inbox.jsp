<%@page session="false" pageEncoding="UTF-8" %>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2" %>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0" %>
<%@taglib prefix="cpp" uri="http://sling.composum.com/cppl/1.0" %>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<cpp:defineFrameObjects/>
<cpp:element var="frame" type="com.composum.pages.stage.model.edit.FrameModel" mode="none"
             cssBase="composum-platform-workflow" cssAdd="composum-pages-tools">
    <div class="composum-pages-tools_actions btn-toolbar">
        <div class="composum-pages-tools_left-actions">
            <div class="composum-pages-tools_button-group btn-group btn-group-sm" role="group">
                <button type="button"
                        class="fa fa-plus start composum-pages-tools_button btn btn-default"
                        title="${cpn:i18n(slingRequest,'Start a Workflow')}"><span
                        class="composum-pages-tools_button-label">${cpn:i18n(slingRequest,'Start')}</span></button>
            </div>
            <div class="composum-pages-tools_button-group btn-group btn-group-sm" role="group">
                <button type="button"
                        class="fa fa-chevron-right process composum-pages-tools_button btn btn-default"
                        title="${cpn:i18n(slingRequest,'Process the selected Task')}"><span
                        class="composum-pages-tools_button-label">${cpn:i18n(slingRequest,'Process')}</span></button>
                <button type="button"
                        class="fa fa-search detail composum-pages-tools_button btn btn-default"
                        title="${cpn:i18n(slingRequest,'Show workflow Details')}"><span
                        class="composum-pages-tools_button-label">${cpn:i18n(slingRequest,'Details')}</span></button>
                <button type="button"
                        class="fa fa-times cancel composum-pages-tools_button btn btn-default"
                        title="${cpn:i18n(slingRequest,'Cancel the selected Task')}"><span
                        class="composum-pages-tools_button-label">${cpn:i18n(slingRequest,'Cancel')}</span></button>
            </div>
        </div>
        <div class="composum-pages-tools_right-actions">
            <div class="composum-pages-tools_button-group btn-group btn-group-sm" role="group">
                <button type="button"
                        class="fa fa-refresh reload composum-pages-tools_button btn btn-default"
                        title="${cpn:i18n(slingRequest,'Reload')}"><span
                        class="composum-pages-tools_button-label">${cpn:i18n(slingRequest,'Reload')}</span></button>
            </div>
        </div>
    </div>
    <div class="composum-pages-tools_panel">
        <sling:include resourceType="composum/platform/workflow/components/inbox/list"/>
    </div>
</cpp:element>
