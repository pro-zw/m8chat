angular.module('m8chatApp.controllers')
    .controller('MyBillController', ['$scope', '$window', '$timeout', 'appUtil', 'alertService', 'paypalService', 'billService', function($scope, $window, $timeout, appUtil, alertService, paypalService, billService) {
        var turnOffAllFlags = function() {
            $scope.isCreatingPayment = false;
            $scope.isRedirecting = false;
        };

        $scope.isProcessing = function () {
            return $scope.isCreatingPayment || $scope.isRedirecting;
        };

        turnOffAllFlags();
        $scope.payBill = function(event) {
            if (event) {
                event.preventDefault();
            }

            var billId = $('#mc-my-bill-id').text();
            if (!billId) {
                alertService.setAlert('my-bill-alert', 'Cannot find bill id', 'alert-danger');
            } else {
                turnOffAllFlags();
                $scope.isCreatingPayment = true;
                paypalService.createPayment(billId)
                    .success(function(data) {
                        turnOffAllFlags();
                        $scope.isRedirecting = true;

                        $timeout(function() {
                            $window.location.href = data.approvalUrl;
                        }, 1200);
                    })
                    .error(function(data, status) {
                        turnOffAllFlags();
                        alertService.setAlert('my-bill-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
                    });
            }
        };
    }]);
