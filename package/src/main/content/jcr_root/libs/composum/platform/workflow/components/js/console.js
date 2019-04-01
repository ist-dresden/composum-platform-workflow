/**
 *
 *
 */
(function (window) {
    'use strict';

    window.workflow = window.workflow || {};

    (function (workflow, core) {

        workflow.InboxTable = workflow.InboxView.extend({

            initialize: function (options) {
                workflow.InboxView.prototype.initialize.apply(this, [options]);
            },

            onTaskSelected: function () {
                this.$selected.find('.sel input').prop('checked', true);
            },

            onTaskAction: function () {
                $(document).trigger('detail:reload', [this.path]);
            },

            onScopeChanged: function (scope) {
                $(document).trigger('scope:changed', [this.path, scope]);
            }
        });

        workflow.onTableLoad = function () {
            var c = workflow.const.css;
            workflow.inboxView = core.getView(
                '.' + c.base + c._inbox, workflow.InboxTable);
            workflow.inboxToolbar = core.getView(
                '.' + c.toolbar.base + ' .' + c.toolbar.base + c.toolbar._toolbar, workflow.InboxToolbar);
        };

        workflow.onTableLoad();

        workflow.InboxConsoleTab = core.console.DetailTab.extend({

            initialize: function (options) {
                core.console.DetailTab.prototype.initialize.apply(this, [options]);
                this.initContent();
            },

            initContent: function () {
                window.widgets.setUp(this.el);
            },

            reloadParameters: function () {
                return {scope: workflow.inboxView.scope};
            }
        });

    })(window.workflow, window.core);

})(window);
