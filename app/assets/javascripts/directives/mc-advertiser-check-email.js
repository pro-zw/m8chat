/**
 * Created by weizheng on 14/12/27.
 */

angular.module('m8chatApp.directives')
    .directive('mcAdvertiserCheckEmail', ['$http', 'appPath', function($http, appPath) {
        return {
            require: 'ngModel',
            restrict: 'A',
            link: function(scope, element, attrs, ctrl) {
                element.on('blur', function() {
                    scope.$apply(function(){
                        // Because checking needs time, and be skipped some time,
                        // we must reset the exists validity to true before checking
                        ctrl.$setValidity('unique', true);

                        if (ctrl.$modelValue) {
                            $http({
                                method: 'POST',
                                url: appPath.apiCheckEmail,
                                data: {'email': ctrl.$modelValue},
                                timeout: 2000
                            }).success(function(data) {
                                ctrl.$setValidity('unique', !data.exists);
                            });
                        }
                    });
                });
            }
        };
    }]);