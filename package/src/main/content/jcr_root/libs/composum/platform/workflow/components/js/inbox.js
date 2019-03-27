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
                this.$scope = this.$('.scope select');
                this.$scope.val(workflow.inboxTable.scope);
                this.$('.start').click(_.bind(workflow.inboxTable.addTask, workflow.inboxTable));
                this.$('.process').click(_.bind(workflow.inboxTable.runTask, workflow.inboxTable));
                this.$('.detail').click(_.bind(workflow.inboxTable.showDetail, workflow.inboxTable));
                this.$('.cancel').click(_.bind(workflow.inboxTable.cancelTask, workflow.inboxTable));
                this.$scope.change(_.bind(workflow.inboxTable.scopeChanged, workflow.inboxTable));
            }
        });

        workflow.InboxTable = Backbone.View.extend({

            initialize: function (options) {
                var c = workflow.const.css;
                this.path = this.$el.data('path');
                this.scope = this.$el.data('scope');
                this.$tasks = this.$('.' + c.base + c._task).click(_.bind(this.selectTask, this));
                this.$selected = [];
            },

            selectTask: function (event) {
                var c = workflow.const.css;
                event.preventDefault();
                this.$selected = $(event.currentTarget).closest('.' + c.base + c._task);
                this.$selected.find('.sel input').prop('checked', true);
                return false;
            },

            addTask: function (event) {
                if (event) {
                    event.preventDefault();
                }
                var u = workflow.const.url;
                core.getHtml(u.base + u._start,
                    _.bind(function (content) {
                        if (content) {
                            core.showFormDialog(workflow.StartDialog, content);
                        }
                    }, this));
                return false;
            },

            runTask: function (event) {
                if (event) {
                    event.preventDefault();
                }
                if (this.$selected.length === 1) {
                    var u = workflow.const.url;
                    var path = this.$selected.data('path');
                    core.getHtml(u.base + u._dialog + path,
                        _.bind(function (content) {
                            if (content) {
                                core.showFormDialog(workflow.Dialog, content);
                            }
                        }, this));
                }
                return false;
            },

            showDetail: function (event) {
                if (event) {
                    event.preventDefault();
                }
                if (this.$selected.length === 1) {
                    var path = this.$selected.data('path');
                    core.getHtml(path + '.dialog.condense.html', _.bind(function (content) {
                        core.showLoadedDialog(core.components.LoadedDialog, content);
                    }, this));
                }
                return false;
            },

            cancelTask: function (event) {
                if (event) {
                    event.preventDefault();
                }
                if (this.$selected.length === 1) {
                    var path = this.$selected.data('path');
                    core.getHtml(path + '.cancel.condense.html', _.bind(function (content) {
                        core.showFormDialog(workflow.CancelDialog, content);
                    }, this));
                }
                return false;
            },

            scopeChanged: function (event) {
                if (event) {
                    event.preventDefault();
                }
                var $select = $(event.currentTarget);
                var scope = $select.val();
                if (this.scope !== scope){
                    this.scope = scope;
                    $(document).trigger('scope:changed', [this.path, scope]);
                }
                return false;
            }
        });

        workflow.onTableLoad = function () {
            var c = workflow.const.css;
            workflow.inboxTable = core.getView(
                '.' + c.base + c._inbox, workflow.InboxTable);
            workflow.inboxToolbar = core.getView(
                '.' + c.toolbar.base + ' .' + c.toolbar.base + c.toolbar._toolbar, workflow.InboxToolbar);
        };

        workflow.onTableLoad();

    })(window.workflow, window.core);

})(window);
