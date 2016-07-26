"use strict";

angular.module('ricashApp',['ricashApp.controllers', 'ngRoute', 'ngAnimate', 'ui.bootstrap']).
    config(["$routeProvider", function($routeProvider, RicashController) {
    $routeProvider.when("/", {controller: "RicashController"});
    $routeProvider.otherwise({redirectTo: "/"});
}]);
