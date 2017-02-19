/**
 * Created by weizheng on 15/2/16.
 */

angular.module('m8chatApp.directives')
    .directive('mcStripeCheckCardNumber', function() {
        return {
            require: 'ngModel',
            restrict: 'A',
            link: function(scope, element, attrs, ctrl) {
                element.on('blur', function() {
                    ctrl.$setValidity('stripe', true);

                    if (ctrl.$modelValue) {
                        ctrl.$setValidity('stripe', Stripe.validateCardNumber(ctrl.$modelValue));
                    }
                });
            }
        };
    });