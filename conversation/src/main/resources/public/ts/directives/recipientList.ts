import { ng, _ } from 'entcore';
import http from 'axios';

/**
 * @description Displays chips of items list with a search input and dropDown options. If more than
 * 4 items are in the list, only 2 of them will be shown.
 * @param ngModel The list of items to display in chips.
 * @param ngChange Called when the items list changed.
 * @param updateFoundItems (search, model, founds) Called to update the dropDown content according to
 * the search input.
 * @example
 * <recipient-list
        ng-model="<model>"
        ng-change="<function>()"
        update-found-items="<function>(search, model, founds)">
    </recipient-list>
 */
export const recipientList = ng.directive('recipientList', () => {
    return {
        restrict: 'E',
        template: `
            <div class="twelve flex-row align-center" ng-click="unfoldChip()">
                <contact-chip class="block relative removable" 
                    ng-model="item"
                    action="deleteItem(item)"
                    ng-repeat="item in ngModel | limitTo : (needChipDisplay() ? 2 : ngModel.length)">
                </contact-chip>
                <label class="chip selected" ng-if="needChipDisplay()" ng-click="giveFocus()">
                    <span class="cell">... <i18n>chip.more1</i18n> [[ngModel.length - 2]] <i18n>chip.more2</i18n></span>
                </label>
                <img skin-src="/img/illustrations/loading.gif" width="30px" heigh="30px" ng-if="loading"/>
                <form class="input-help" ng-submit="update(true)">
                    <input class="chip-input right-magnet" type="text" ng-model="search.text" ng-change="update()" autocomplete="off" ng-class="{ move: search.text.length > 0 }" 
                    i18n-placeholder="[[restriction ? 'share.search.help' : 'share.search.placeholder' ]]"
                    />    
                </form>
                <drop-down
                    options="itemsFound"
                    ng-change="addItem()"
                    on-close="clearSearch()"
                    ng-model="currentReceiver">
                </drop-down>
            </div>

            <lightbox show="sharebookmark.excluded.length > 0" on-close="sharebookmark.excluded = []">
                <h2><i18n>warning.title</i18n></h2>
                <span class="bottom-spacing-twice">
                    <i18n>warning.excluded</i18n>
                </span>
                <table class="twelve">
                    <thead>
                    <tr>
                        <th class="" ng-click=""><i18n>name</i18n></th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr ng-repeat="e in sharebookmark.excluded">
                        <td class="user">[[e.displayName || e.name]]</td>
                    </tr>
                    </tbody>
                </table>
                <button type="button" class="cancel right-magnet" ng-click="sharebookmark.excluded = []"><i18n>warning.close</i18n></button>
            </lightbox>
        `,

        scope: {
            ngModel: '=',
            ngChange: '&',
            restriction: '=',
            updateFoundItems: '&'
        },

        link: (scope, element, attributes) => {
            var firstFocus = true;
            var minWidth = 0;
            scope.focused = false;
            scope.loading = false;
            scope.search = {
                text: ''
            };
            scope.itemsFound = [];
            scope.currentReceiver = 'undefined';
            scope.addedFavorites = [];
            scope.sharebookmark = {
                excluded: []
            }

            element.find('input').on('focus', () => {
                if (firstFocus)
                    firstFocus = false;
                scope.focused = true;
                element.find('div').addClass('focus');
                element.find('form').width(minWidth);
            });

            element.find('input').on('blur', () => {
                scope.focused = false;
                element.find('div').removeClass('focus');
                setTimeout(function(){
                    if (!scope.focused) {
                        element.find('form').width(0);
                        scope.itemsFound = [];
                    }
                }, 250);
            });

            element.find('input').on('keydown', function (e) {
                if (e.keyCode === 8 && scope.search.text && scope.search.text.length === 0) { // BackSpace
                    var nb = scope.ngModel.length;
                    if (nb > 0)
                        scope.deleteItem(scope.ngModel[nb - 1]);
                }
            });

            //prevent blur when look for more users in dropDown
            element.parents().find('.display-more').on('click', () => {
                if (!firstFocus) {
                    scope.giveFocus();
                }
            });


            scope.needChipDisplay = () => {
                return !scope.focused && (typeof scope.ngModel !== 'undefined') && scope.ngModel.length > 3;
            };

            scope.update = async (force?: boolean) => {
                scope.addedFavorites = [];
                
                if (force) {
                    await scope.doSearch();
                }
                else {
                    if(scope.restriction && scope.search.text.length < 3 || scope.search.text.length < 1) {
                        scope.itemsFound.splice(0, scope.itemsFound.length);
                    }
                    else {
                        await scope.doSearch();
                    }
                }
            };

            scope.giveFocus = () => {
                if (!scope.focus)
                    element.find('input').focus();
            };

            scope.unfoldChip = () => {
                if (!firstFocus && scope.needChipDisplay()) {
                    scope.giveFocus();
                }
            };


            scope.addOneItem = (item) => {
                for (var i = 0, l = scope.ngModel.length; i < l; i++) {
                    if (scope.ngModel[i].id === item.id) {
                        return false;
                    }
                }
                scope.ngModel.push(item);
                return true;
            }

            scope.addItem = async () => {
                scope.focused = true;
                element.find('input').focus();
                if (!scope.ngModel) {
                    scope.ngModel = [];
                }
                if (scope.currentReceiver.type === 'sharebookmark') {
                    scope.loading = true;
                    // get sharebookmark members
                    var response = await http.get('/directory/sharebookmark/' + scope.currentReceiver.id);
                    // check if sharebookmark members are visible
                    let ids = [];
                    response.data.groups.forEach(group => ids.push(group.id));
                    response.data.users.forEach(user => ids.push(user.id));
                    let visiblesRes = await http.post(`/conversation/visibles`, {ids: ids});
                    // if visible add them to email recipients
                    let visibleIds = visiblesRes.data.map(x => x['visibles.id'])
                    response.data.groups.forEach(group => {
                        if (visibleIds.includes(group.id)) {
                            scope.addOneItem(group);
                        }
                    });
                    response.data.users.forEach(user => {
                        if (visibleIds.includes(user.id)) {
                            scope.addOneItem(user);
                        }
                    });
                    if (visibleIds.length < ids.length) {
                        scope.search.text = '';
                        scope.itemsFound = [];
                        response.data.groups.forEach(g => {
                            if (!visibleIds.includes(g.id)) {
                                scope.sharebookmark.excluded.push(g);
                            }
                        });
                        response.data.users.forEach(u => {
                            if (!visibleIds.includes(u.id)) {
                                scope.sharebookmark.excluded.push(u);
                            }
                        });
                    }
                    scope.addedFavorites.push(scope.currentReceiver);
                    scope.loading = false;
                }
                else {
                    scope.ngModel.push(scope.currentReceiver);
                    setTimeout(function () {
                        scope.itemsFound.splice(scope.itemsFound.indexOf(scope.currentReceiver), 1);
                        scope.$apply('itemsFound');
                    }, 0);
                }
                scope.$apply('ngModel');
                scope.$eval(scope.ngChange);
            };

            scope.deleteItem = async (item) => {
                scope.ngModel = _.reject(scope.ngModel, function (i) { return i === item; });
                scope.$apply('ngModel');
                scope.$eval(scope.ngChange);
                if (scope.itemsFound.length > 0) {
                    setTimeout(async function () {
                        await scope.doSearch();
                    }, 0);
                }
            };

            scope.clearSearch = () => {
                if (!scope.focused) {
                    scope.search.text = '';
                    scope.itemsFound = [];
                }
            };

            scope.doSearch = async () => {
                var i, element;
                await scope.updateFoundItems({search:scope.search, model:scope.ngModel, founds:scope.itemsFound, restriction:scope.restriction});
                for (i = scope.itemsFound.length - 1; i >= 0; i--) {
                    element = _.findWhere(scope.addedFavorites, { id: scope.itemsFound[i].id });
                    if (element) {
                        scope.itemsFound.splice(scope.itemsFound.indexOf(element), 1);
                    }
                }
                scope.$apply('itemsFound');
            };

            // Focus when items list changes
            scope.$watchCollection('ngModel', function(newValue){
                if (!firstFocus) {
                    scope.giveFocus();
                }
            });



            // Make the input width be the label help infos width
            setTimeout(function(){
                minWidth = element.find('form label').width();
            }, 0);
        }
    };
});
