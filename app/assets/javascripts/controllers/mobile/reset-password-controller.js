/**
 * Created by weizheng on 15/1/6.
 */

angular.module('m8chatAppMobile.controllers')
    .controller('ResetPasswordController', ['$scope', '$location', '$http', 'appUtil', function($scope, $location, $http, appUtil) {
        $scope.isResetting = false;
        $scope.errorMessage = '';
        $scope.isReset = false;

        $scope.reset = function() {
            $scope.isResetting = true;

            $http.post($location.absUrl(), {
                plainPassword: $scope.resetModel.password
            }).success(function() {
                $scope.isResetting = false;
                $scope.isReset = true;
                $scope.errorMessage = '';
            }).error(function(data, status) {
                $scope.isResetting = false;
                $scope.isReset = false;
                $scope.errorMessage = appUtil.parseErrorMessage(data, status);
            });
        };
    }]);
