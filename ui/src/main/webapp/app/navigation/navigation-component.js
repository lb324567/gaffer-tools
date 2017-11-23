/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


angular.module('app').component('navBar', navBar());

function navBar() {
    return {
        templateUrl: 'app/navigation/navigation.html',
        controller: NavigationController,
        controllerAs: 'ctrl'
    };
}

function NavigationController($scope, $rootScope, $mdDialog, navigation, graph, operationService, results, query, config, loading, properties) {

    var vm = this;
    vm.addMultipleSeeds = false;
    vm.appTitle;

    var defaultTitle = "Gaffer"

    config.get().then(function(conf) {
        if (conf.title) {
            vm.appTitle = conf.title;
            return;
        }
        properties.get().then(function(props) {
            if (props) {
                var configuredTitle = props["gaffer.properties.app.title"]
                if (configuredTitle) {
                    vm.appTitle = configuredTitle;
                    return;
                }
            }

            vm.appTitle = defaultTitle;

        },
        function(err) {
            vm.appTitle = defaultTitle;
        });




    })

    vm.currentPage = navigation.getCurrentPage();

    navigation.observeCurrentPage().then(null, null, function(newCurrentPage) {
        vm.currentPage = newCurrentPage
    })

    vm.goTo = navigation.goTo;

    $rootScope.$on('$routeChangeSuccess', function (event, current) {
        var newPage = current.originalPath.substr(1);
        if (newPage !== vm.currentPage) {
            navigation.goTo(newPage);
        }
    });

    vm.addSeedPrompt = function(ev) {
        $mdDialog.show({
            preserveScope: true,
            template: '<seed-builder-dialog aria-label="Seed Builder"></seed-builder-dialog>',
            parent: angular.element(document.body),
            targetEvent: ev,
            clickOutsideToClose: true
        })
        .then(function(seeds) {
            for(var i in seeds) {
                graph.addSeed(seeds[i]);
            }
        })
        .catch(function(){}); // throw away possibly unhandled rejection errors
    }

    vm.isGraphInView = function() {
        return vm.currentPage === 'graph';
    }

    vm.redraw = function() {
        graph.redraw();
    }

    var recursivelyExecuteOperations = function(opIndex, ops) {
        try {
            query.execute(JSON.stringify({
                class: "uk.gov.gchq.gaffer.operation.OperationChain",
                operations: [ops[opIndex], operationService.createLimitOperation(), operationService.createDeduplicateOperation()]
            }), function(data) {
                results.update(data);
                if((opIndex + 1) < ops.length) {
                    recursivelyExecuteOperations(opIndex + 1, ops);
                } else {
                    loading.finish();
                }
            });
        } catch(e) {
            // Try without the limit and deduplicate operations
            query.execute(JSON.stringify({
                class: "uk.gov.gchq.gaffer.operation.OperationChain",
                operations: [ops[opIndex]]
            }), function(data) {
                results.update(data);
                if((opIndex + 1) < ops.length) {
                    recursivelyExecuteOperations(opIndex + 1, ops);
                } else {
                    loading.finish();
                }
            });
       }
    }
    vm.executeAll = function() {
        results.clear();
        var ops = query.getOperations();

        if (ops.length > 0) {
            loading.load();
            recursivelyExecuteOperations(0, ops);
        }
    }
}
