/**
 * Created by weizheng on 15/2/9.
 */

angular.module('m8chatApp.controllers')
    .controller('MyPaymentMethodController', ['$scope', '$window', 'appUtil', 'alertService', 'advertiserService', function($scope, $window, appUtil, alertService, advertiserService) {
        $scope.myPaymentMethodModel = {};
        $scope.isSaving = false;
        $scope.isDeletingCard = false;
        $scope.isRetrievingCard = false;

        $scope.validateMonth = function(value) {
            return parseInt(value) > 0 && parseInt(value) <= 12;
        };

        $scope.myCreditCardInfo = {};
        if ($("input[name='cardLast4Digits']").length > 0) {
            $scope.isRetrievingCard = true;

            advertiserService.getCreditCard()
                .success(function(data) {
                    $scope.isRetrievingCard = false;

                    $scope.myCreditCardInfo.holder = data.holder;
                    $scope.myCreditCardInfo.last4Digits = data.last4Digits;
                    $scope.myCreditCardInfo.expiryMonth = data.expiryMonth;
                    $scope.myCreditCardInfo.expiryYear = data.expiryYear;
                })
                .error(function(data, status) {
                    $scope.isRetrievingCard = false;
                    alertService.setAlert('my-payment-method-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
                });
        }

        $scope.save = function() {
            $scope.isSaving = true;
            alertService.clear();

            if ($scope.myPaymentMethodModel.paymentMethod == 'subscription' &&
                $scope.myPaymentMethodModel.cardNumber) {
                if (!Stripe.validateExpiry($scope.myPaymentMethodModel.cardExpiryMonth,
                        $scope.myPaymentMethodModel.cardExpiryYear)) {
                    $scope.isSaving = false;
                    alertService.setAlert('my-payment-method-alert', 'The expiry date appears to be invalid', 'alert-danger');
                    return;
                }

                Stripe.card.createToken({
                    name: $scope.myPaymentMethodModel.cardHolder,
                    number: $scope.myPaymentMethodModel.cardNumber,
                    cvc: $scope.myPaymentMethodModel.cardCvc,
                    exp_month: $scope.myPaymentMethodModel.cardExpiryMonth,
                    exp_year: $scope.myPaymentMethodModel.cardExpiryYear
                }, function(status, response) {
                    if (response.error) {
                        $scope.isSaving = false;
                        alertService.setAlert('my-payment-method-alert', response.error.message, 'alert-danger');
                    } else {
                        var cardToken = response.id;
                        advertiserService.updatePaymentMethod($scope.myPaymentMethodModel.paymentMethod, cardToken)
                            .success(function() {
                                $scope.isSaving = false;
                                alertService.setAlert('my-payment-method-alert', 'Saved!', 'alert-success');
                                $window.location.reload();
                            })
                            .error(function(data, status) {
                                $scope.isSaving = false;
                                alertService.setAlert('my-payment-method-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
                            });
                    }
                });
            } else {
                advertiserService.updatePaymentMethod($scope.myPaymentMethodModel.paymentMethod)
                    .success(function() {
                        $scope.isSaving = false;
                        alertService.setAlert('my-payment-method-alert', 'Saved!', 'alert-success');
                        $window.location.reload();
                    })
                    .error(function(data, status) {
                        $scope.isSaving = false;
                        alertService.setAlert('my-payment-method-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
                    });
            }
        };

        $scope.deleteCard = function() {
            $scope.isDeletingCard = true;

            advertiserService.removeStripeCustomerId()
                .success(function() {
                    $scope.isDeletingCard = false;
                    alertService.setAlert('my-payment-method-alert', 'Deleted!', 'alert-success');
                    $window.location.reload();
                })
                .error(function(data, status) {
                    $scope.isDeletingCard = false;
                    alertService.setAlert('my-payment-method-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
                });
        };
    }]);