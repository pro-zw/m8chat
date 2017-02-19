/**
 * Created by weizheng on 15/1/6.
 */

angular.module('m8chatApp.controllers')
    .controller('MyAccountDetailsController', ['$scope', 'advertiserService', 'alertService', 'appUtil', function($scope, advertiserService, alertService, appUtil) {
        $scope.myAccountModel = {};
        $scope.isSaving = false;

        $scope.save = function() {
            $scope.isSaving = true;

            advertiserService.updateAccount($scope.myAccountModel)
                .success(function() {
                    $scope.isSaving = false;
                    alertService.setAlert('my-account-details-alert', 'Saved!', 'alert-success');
                })
                .error(function(data, status) {
                    $scope.isSaving = false;
                    alertService.setAlert('my-account-details-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
                });
        };
    }]);
