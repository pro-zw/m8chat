angular.module('m8chatApp.filters', []);

angular.module('m8chatApp.services', [
    'm8chatAppCommon.services',
    'm8chatApp.filters'
]);

angular.module('m8chatApp.directives', [
    'm8chatAppCommon.directives',
    'm8chatApp.filters',
    'm8chatApp.services'
]);

angular.module('m8chatApp.controllers', [
    'm8chatApp.filters',
    'm8chatApp.services',
    'm8chatApp.directives'
]);

angular.module('m8chatApp', [
    'ngCookies',
    'ngSanitize',
    'ngTouch',
    'ui.utils',
    'ui.bootstrap',
    'm8chatApp.filters',
    'm8chatApp.services',
    'm8chatApp.directives',
    'm8chatApp.controllers'
]).config(['$httpProvider', '$locationProvider', function($httpProvider, $locationProvider) {
    $httpProvider.interceptors.push('apiErrorInterceptor');
    $httpProvider.defaults.withCredentials = true;

    $locationProvider.html5Mode(false).hashPrefix('!');
}]);