/**
 * Created by weizheng on 14/12/30.
 */

angular.module('m8chatApp.directives')
    .directive('mcCheckAddress', function() {
        return {
            require: 'ngModel',
            restrict: 'A',
            link: function(scope, element, attrs, ctrl) {
                element.on('blur', function() {
                    scope.$apply(function() {
                        // Because checking needs time, and be skipped some time,
                        // we must reset the exists validity to true before checking
                        ctrl.$setValidity('address', true);

                        if (ctrl.$modelValue) {
                            var geocoder = new google.maps.Geocoder();
                            var address = ctrl.$modelValue.trim();
                            geocoder.geocode({'address': address}, function(results, status) {
                                if (status == google.maps.GeocoderStatus.OK && results.length == 1) {
                                    ctrl.$setValidity('address', true);
                                } else if (address.indexOf("/") > 0 && address.indexOf("/") < address.length - 1) {
                                    address = address.slice(address.indexOf("/") + 1);

                                    geocoder.geocode( {'address': address}, function(results, status) {
                                        if (status == google.maps.GeocoderStatus.OK && results.length == 1) {
                                            ctrl.$setValidity('address', true);
                                        } else {
                                            ctrl.$setValidity('address', false);
                                        }
                                    });
                                } else {
                                    ctrl.$setValidity('address', false);
                                }
                            });
                        }
                    });
                });

                element.on('keydown', function() {
                    scope.$apply(function() {
                        ctrl.$setValidity('address', true);
                    });
                });
            }
        };
    });