/**
 * Created by weizheng on 15/1/13.
 */

angular.module('m8chatApp.controllers')
    .controller('ExecutePaymentController', ['$scope', '$window', '$timeout', '$http', '$location', 'appUtil', function($scope, $window, $timeout, $http, $location, appUtil) {
        $scope.isExecuting = true;
        $scope.message = "Please don't refresh the page or navigate to another page";

        $http.post($location.absUrl(), {})
            .success(function() {
                $scope.isExecuting = false;
                $scope.message = 'Payment executed successfully. Loading your account...';

                $timeout(function(){
                    $window.location.href = '/advert/advertisers/me/bill';
                }, 1200);
            })
            .error(function(data, status) {
                $scope.isExecuting = false;
                $scope.message = appUtil.parseErrorMessage(data, status);
            });
    }]);
