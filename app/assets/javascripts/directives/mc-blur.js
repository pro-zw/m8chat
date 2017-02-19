/**
 * Created by weizheng on 27/11/14.
 */

angular.module('m8chatAppCommon.directives')
    .directive('mcBlur', function () {
        return {
            require: 'ngModel',
            restrict: 'A',
            link: function(scope, element, attrs, ctrl) {
                ctrl.$blurred = false;

                element.on('keydown', function() {
                    scope.$apply(function() {
                        ctrl.$blurred = false;
                    });
                });
                element.on('blur', function() {
                    scope.$apply(function() {
                        ctrl.$blurred = true;
                    });
                });
            }
        };
    });