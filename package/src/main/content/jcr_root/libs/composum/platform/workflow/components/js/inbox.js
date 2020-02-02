/**
 *
 *
 */
(function (window) {
    'use strict';

    window.workflow = window.workflow || {};

    (function (workflow, core) {

        workflow.InboxView = Backbone.View.extend({

            initialize: function (options) {
                this.path = this.$el.data('path');
                this.scope = this.$el.data('scope');
                this.initContent();
            },

            initContent: function () {
                var c = workflow.const.css;
                this.path = this.$el.data('path');
                this.$tasks = this.$('.' + c.base + c._task).click(_.bind(this.selectTask, this));
                this.$selected = [];
            },

            selectTask: function (event) {
                var c = workflow.const.css;
                event.preventDefault();
                this.$selected = $(event.currentTarget).closest('.' + c.base + c._task);
                if (_.isFunction(this.onTaskSelected)) {
                    this.onTaskSelected();
                }
                workflow.inboxToolbar.adjustState(
                    this.$selected.data('state'),
                    this.$selected.data('graph'),
                    this.$selected.data('cancel'));
                return false;
            },

            addTask: function (event) {
                if (event) {
                    event.preventDefault();
                }
                var u = workflow.const.url;
                core.getHtml(u.base + u._start + this.path,
                    _.bind(function (content) {
                        if (content) {
                            core.showFormDialog(workflow.StartDialog, content, {}, undefined, _.bind(function () {
                                if (_.isFunction(this.onTaskAction)) {
                                    this.onTaskAction();
                                }
                            }, this));
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
                                core.showFormDialog(workflow.Dialog, content, {}, undefined, _.bind(function () {
                                    if (_.isFunction(this.onTaskAction)) {
                                        this.onTaskAction();
                                    }
                                }, this));
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
                        }, this)
                    );
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
                        core.showFormDialog(workflow.CancelDialog, content, {}, undefined, _.bind(function () {
                            if (_.isFunction(this.onTaskAction)) {
                                this.onTaskAction();
                            }
                        }, this));
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
                if (this.scope !== scope) {
                    this.scope = scope;
                    if (_.isFunction(this.onScopeChanged)) {
                        this.onScopeChanged(scope);
                    }
                }
                return false;
            }
        });

    })(window.workflow, window.core);

})(window);
