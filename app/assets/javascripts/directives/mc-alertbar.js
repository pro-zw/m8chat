/**
 * Created by weizheng on 28/11/14.
 */

angular.module('m8chatApp.directives')
    .directive('mcAlertBar', ['alertService', function (alertService) {
        return {
            templateUrl: '/assets/javascripts/templates/alertbar.html',
            restrict: 'A',
            replace: true,
            scope: {
                topic: '@'
            },
            link: function(scope) {
                scope.alertService = alertService;
            }
        };
    }]);