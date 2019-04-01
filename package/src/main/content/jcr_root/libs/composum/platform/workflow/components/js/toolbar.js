/**
 *
 *
 */
(function (window) {
    'use strict';

    window.workflow = window.workflow || {};

    (function (workflow, core) {

        workflow.InboxToolbar = Backbone.View.extend({

            initialize: function (options) {
                this.$start = this.$('.start');
                this.$process = this.$('.process');
                this.$detail = this.$('.detail');
                this.$cancel = this.$('.cancel');
                this.$scope = this.$('.scope select');
                this.initHandlers();
            },

            initHandlers: function () {
                this.$scope.val(workflow.inboxView.scope);
                this.$start.off('click').click(_.bind(workflow.inboxView.addTask, workflow.inboxView));
                this.$process.off('click').click(_.bind(workflow.inboxView.runTask, workflow.inboxView));
                this.$detail.off('click').click(_.bind(workflow.inboxView.showDetail, workflow.inboxView));
                this.$cancel.off('click').click(_.bind(workflow.inboxView.cancelTask, workflow.inboxView));
                this.$scope.off('change').change(_.bind(workflow.inboxView.scopeChanged, workflow.inboxView));
            },

            adjustState: function (state, graph, cancel) {
                switch (state) {
                    case 'pending':
                        this.$process.prop('disabled', false);
                        this.$detail.prop('disabled', !graph);
                        this.$cancel.prop('disabled', !cancel || !graph);
                        break;
                    case 'running':
                        this.$process.prop('disabled', true);
                        this.$detail.prop('disabled', !graph);
                        this.$cancel.prop('disabled', !cancel || !graph);
                        break;
                    case 'finished':
                        this.$process.prop('disabled', true);
                        this.$detail.prop('disabled', !graph);
                        this.$cancel.prop('disabled', true);
                        break;
                    default:
                        this.$process.prop('disabled', true);
                        this.$detail.prop('disabled', true);
                        this.$cancel.prop('disabled', true);
                        break;
                }
            }
        });

    })(window.workflow, window.core);

})(window);
