/**
 * Created by weizheng on 15/12/14.
 */

angular.module('m8chatApp.controllers')
    .controller('RegisterController', ['$scope', 'appUtil', 'advertiserService', 'alertService', function($scope, appUtil, advertiserService, alertService) {
        var plan1Button = $('#mc-advert-plan-button1');
        var plan2Button = $('#mc-advert-plan-button2');

        plan1Button.text('Select');
        plan2Button.text('Select');

        $scope.isRegistering = false;
        $scope.registerModel = {};

        var clearSelection = function() {
            $('.mc-advert-plan-column1').removeClass('mc-advert-plan-column-selected');
            $('.mc-advert-plan-column2').removeClass('mc-advert-plan-column-selected');

            plan1Button.removeClass('btn-primary');
            plan1Button.addClass('btn-default');
            plan1Button.text('Select');

            plan2Button.removeClass('btn-primary');
            plan2Button.addClass('btn-default');
            plan2Button.text('Select');

            // 0 means nothing is selected
            $scope.registerModel.planSelected = 0;
        };

        $scope.advertPlanSelected = function(planIndex) {
            clearSelection();

            switch(planIndex) {
                case 1:
                    $('.mc-advert-plan-column1').addClass('mc-advert-plan-column-selected');
                    plan1Button.removeClass('btn-default');
                    plan1Button.addClass('btn-primary');
                    plan1Button.text('Selected');
                    break;
                case 2:
                    $('.mc-advert-plan-column2').addClass('mc-advert-plan-column-selected');
                    plan2Button.removeClass('btn-default');
                    plan2Button.addClass('btn-primary');
                    plan2Button.text('Selected');
                    break;
                case 3:
                    $window.location.href = '/#mc-contact-us-anchor';
                    break;
                default:
                    break;
            }

            if (planIndex >= 0 && planIndex <= 3) {
                $scope.registerModel.planSelected = planIndex;
            } else {
                $scope.registerModel.planSelected = 0;
            }
        };

        var planPreSelected = appUtil.getQueryParam('plan-pre-selected') ? parseInt(appUtil.getQueryParam('plan-pre-selected')) : 0;
        $scope.advertPlanSelected(planPreSelected);

        $scope.register = function() {
            $scope.isRegistering = true;
            advertiserService.register($scope.registerModel)
                .success(function() {
                    $scope.isRegistering = false;
                    alertService.setAlert('register-alert', 'Registered successfully! A confirmation email will be sent to you shortly - click the link in the email to verify your registration.', 'alert-success');
                })
                .error(function(data, status) {
                    $scope.isRegistering = false;
                    alertService.setAlert('register-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
                });
        };
    }]);