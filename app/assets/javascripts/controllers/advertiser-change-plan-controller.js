/**
 * Created by weizheng on 15/2/5.
 */

angular.module('m8chatApp.controllers')
    .controller('AdvertiserChangePlanController', ['$scope', '$modalInstance', 'advertiserService', 'appUtil', 'alertService', 'planName', function($scope, $modalInstance, advertiserService, appUtil, alertService, planName) {
        $scope.isChanging = false;
        $scope.planName = planName.charAt(0).toUpperCase() + planName.slice(1);

        $scope.ok = function() {
            $scope.isChanging = true;

            advertiserService.changePlan(planName)
                .success(function () {
                    $scope.isChanging = false;
                    alertService.clear();
                    $modalInstance.close('changed');
                }).error(function (data, status) {
                    $scope.isChanging = false;
                    alertService.setAlert('advertiser-change-plan', appUtil.parseErrorMessage(data, status), 'alert-danger');
                });
        };

        $scope.cancel = function() {
            alertService.clear();
            $modalInstance.dismiss('canceled');
        };
    }]);