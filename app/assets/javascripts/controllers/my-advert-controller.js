/**
 * Created by weizheng on 14/12/30.
 */

angular.module('m8chatApp.controllers')
    .controller('MyAdvertController', ['$scope', 'advertiserService', 'alertService', 'appUtil', function($scope, advertiserService, alertService, appUtil) {
        $scope.myAdvertModel = {};
        $scope.isSaving = false;

        var updateBusiness = function() {
            advertiserService.updateBusiness($scope.myAdvertModel)
                .success(function() {
                    $scope.isSaving = false;
                    $scope.canUploadPhotos = true;
                    alertService.setAlert('update-business-alert', 'Saved!', 'alert-success');
                })
                .error(function(data, status) {
                    $scope.isSaving = false;
                    alertService.setAlert('update-business-alert', appUtil.parseErrorMessage(data, status), 'alert-danger');
                });
        };

        $scope.save = function() {
            $scope.isSaving = true;

            var geocoder = new google.maps.Geocoder();
            var address = $scope.myAdvertModel.address.trim();
            geocoder.geocode({'address': address}, function(results, status) {
                if (status == google.maps.GeocoderStatus.OK && results.length == 1) {
                    $scope.myAdvertModel.latitude = results[0].geometry.location.lat();
                    $scope.myAdvertModel.longitude = results[0].geometry.location.lng();

                    updateBusiness();
                } else if (address.indexOf("/") > 0 && address.indexOf("/") < address.length - 1) {
                    address = address.slice(address.indexOf("/") + 1);

                    geocoder.geocode({'address': address}, function(results, status) {
                        if (status == google.maps.GeocoderStatus.OK && results.length == 1) {
                            $scope.myAdvertModel.latitude = results[0].geometry.location.lat();
                            $scope.myAdvertModel.longitude = results[0].geometry.location.lng();

                            updateBusiness();
                        } else {
                            $scope.isSaving = false;
                            $scope.$apply(function(){
                                $scope.myAdvertForm.address.$setValidity('address', false);
                            });
                        }
                    });
                } else {
                    $scope.isSaving = false;
                    $scope.$apply(function(){
                        $scope.myAdvertForm.address.$setValidity('address', false);
                    });
                }
            });
        };
    }]);
