/**
 *
 *
 */
(function (window) {
    'use strict';

    window.workflow = window.workflow || {};

    (function (workflow, core) {

        workflow.DialogOption = Backbone.View.extend({

            initialize: function (options) {
                var c = workflow.const.css;
                this.$radio = this.$('.' + c.base + c._option + c._radio);
                this.$label = this.$('.' + c.base + c._option + c._label);
                this.$form = this.$('.' + c.base + c._option + c._form);
                this.$label.click(_.bind(this.selectOption, this));
                this.$radio.change(_.bind(this.optionSelected, this));
            },

            selectOption: function (event) {
                if (event) {
                    event.preventDefault();
                }
                this.$radio.prop('checked', true);
                this.optionSelected();
                return false;
            },

            optionSelected: function (event) {
                if (event) {
                    event.preventDefault();
                }
                this.dialog.$forms.removeClass('fade in').addClass('hidden');
                // 'name' -> 'data-name' to avoid side effects between the options
                this.dialog.$forms.find('.widget [name]').each(function () {
                    var $this = $(this);
                    var name = $this.attr('name');
                    $this.removeAttr('name').attr('data-name', name);
                });
                var chosen = this.dialog.getChosenOption();
                if (chosen) {
                    // 'data-name' -> 'name' for the chosen option
                    chosen.$form.find('.widget [data-name]').each(function () {
                        var $this = $(this);
                        var name = $this.attr('data-name');
                        $this.removeAttr('data-name').attr('name', name);
                    });
                    chosen.$form.addClass('fade in').removeClass('hidden')
                }
                return false;
            }
        });

        workflow.Dialog = core.components.FormDialog.extend({

            initialize: function (config) {
                var c = workflow.const.css;
                core.components.FormDialog.prototype.initialize.apply(this, [config]);
                this.$options = this.$('.' + c.dialog.base + c.dialog._options);
                this.$radios = this.$options.find('.' + c.base + c._option + c._radio);
                this.$forms = this.$options.find('.' + c.base + c._option + c._form);
                var dialog = this;
                var options = this.options = [];
                this.$options.find('.' + c.base + c._option).each(function () {
                    var option = core.getView(this, workflow.DialogOption);
                    option.dialog = dialog;
                    options.push(option);
                });
            },

            getChosenOption: function () {
                var c = workflow.const.css;
                var $chosen = this.$options.find('.' + c.base + c._option + c._radio + ':checked')
                    .closest('.' + c.base + c._option);
                return $chosen.length > 0 ? $chosen[0].view : undefined;
            },

            validateForm: function () {
                var result = core.components.FormDialog.prototype.validateForm.apply(this);
                var chosenOption = this.getChosenOption();
                if (!chosenOption && this.options.length > 0) {
                    this.validationHint('error', 'Option', 'an option must be chosen');
                    result = false;
                }
                return result;
            },

            doSubmit: function (callback) {
                this.submitForm(callback);
            }
        });

        workflow.StartDialog = core.components.FormDialog.extend({

            initialize: function (config) {
                var c = workflow.const.css;
                core.components.FormDialog.prototype.initialize.apply(this, [config]);
                this.$content = this.$('.' + c.base + c.dialog._start);
                var $items = this.$content.find('.' + c.base + c.dialog._item);
                $items.click(_.bind(this.selectTask, this));
            },

            selectTask: function (event) {
                var c = workflow.const.css;
                event.preventDefault();
                var $selected = $(event.currentTarget).closest('.' + c.base + c.dialog._item);
                if ($selected.length === 1) {
                    var path = $selected.data('path');
                    var form = $selected.data('form');
                    var title = $selected.find('.' + c.dialog.title).text();
                    var $hint = $selected.find('.' + c.dialog.hint);
                    this.$el.find('.' + c.dialog.base + c.dialog._title/* + ' .' + c.dialog.type*/).text(title);
                    this.$content.html($hint.length === 1 ? $hint[0].outerHTML : '');
                    this.$content.append('<input name="wf.template" type="hidden" value="' + path + '"/>');
                    if (form) {
                        core.getHtml(path + '.start.html' + this.$el.data('path'), _.bind(function (content) {
                            this.$content.append(content);
                            this.setupForm();
                        }, this));
                    } else {
                        this.setupForm();
                    }
                }
                return false;
            },

            setupForm: function () {
                this.setUpWidgets(this.$content);
                var $target = this.$content.find('input[name="wf.target"]');
                var path = this.$el.data('path');
                if ($target.length > 0 && path && path.indexOf('/') === 0 && path.length > 1) {
                    $($target[0]).val(path);
                }
            },

            doSubmit: function (callback) {
                this.submitForm(callback);
            }
        });

        workflow.CancelDialog = core.components.FormDialog.extend({

            doSubmit: function (callback) {
                this.submitForm(callback);
            }
        });

    })(window.workflow, window.core);

})(window);
