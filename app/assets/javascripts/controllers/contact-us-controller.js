/**
 * Created by weizheng on 26/11/14.
 */

angular.module('m8chatApp.controllers')
    .controller('ContactUsController', ['$scope', 'contactUsService', 'alertService', 'appUtil',
        function($scope, contactUsService, alertService, appUtil) {
        $scope.isSending = false;
        $scope.contactUsModel = {};

        $scope.send = function () {
            $scope.isSending = true;

            contactUsService.send($scope.contactUsModel)
                .success(function() {
                    $scope.isSending = false;
                    alertService.setAlert('contact-us-alert', 'Sent successfully', 'alert-success');
                })
                .error(function(data, status) {
                    $scope.isSending = false;
                    alertService.setAlert('contact-us-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
                });
        };
    }]);