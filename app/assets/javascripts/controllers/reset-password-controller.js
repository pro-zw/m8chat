/**
 * Created by weizheng on 15/1/7.
 */

angular.module('m8chatApp.controllers')
    .controller('ResetPasswordController', ['$scope', '$location', '$http', 'alertService', 'appUtil', function($scope, $location, $http, alertService, appUtil) {
        $scope.isResetting = false;
        $scope.isReset = false;

        $scope.reset = function() {
            $scope.isResetting = true;
            $http.post($location.absUrl(), {
                plainPassword: $scope.resetModel.password
            }).success(function() {
                $scope.isResetting = false;
                $scope.isReset = true;
                alertService.setAlert('reset-password-alert', 'Password is reset. Now you can login with new password', 'alert-success');
            }).error(function(data, status) {
                $scope.isResetting = false;
                $scope.isReset = false;
                alertService.setAlert('reset-password-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
            });
        };
    }]);
