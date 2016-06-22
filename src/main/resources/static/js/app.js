/**
 * Created by rslima on 15/02/16.
 */
"use strict";

angular.module("ricashApp",["ricashApp.controllers"]).
    config(["$routeProvider", function($routeProvider, RicashController) {
    $routeProvider.when("/", {controller: "RicashController"});
    $routeProvider.otherwise({redirectTo: "/"});
}]);
