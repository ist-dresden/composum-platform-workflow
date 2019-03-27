/**
 *
 *
 */
(function (window) {
    'use strict';

    window.workflow = window.workflow || {};

    (function (workflow, core) {

        workflow.InboxConsoleTab = core.console.DetailTab.extend({

            initialize: function (options) {
                core.console.DetailTab.prototype.initialize.apply(this, [options]);
                this.initContent();
            },

            initContent: function () {
                window.widgets.setUp(this.el);
            },

            reloadParameters: function () {
                return {scope: workflow.inboxTable.scope};
            }
        });

    })(window.workflow, window.core);

})(window);
