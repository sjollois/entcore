import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Data, NavigationEnd, Router } from '@angular/router';
import { Subscription } from 'rxjs/Subscription';

import { routing } from '../core/services';
import { GroupsStore } from './groups.store';
import { CommunicationRulesService } from '../communication/communication-rules.service';

@Component({
    selector: 'groups-root',
    template: `
        <div class="flex-header">
            <h1><i class="fa fa-users"></i>
                <s5l>groups</s5l>
            </h1>

            <button [routerLink]="['manualGroup', 'create']"
                    [class.hidden]="createButtonHidden()">
                <s5l>create.group</s5l>
                <i class="fonticon group_add is-size-3"></i>
            </button>
        </div>

        <div class="tabs">
            <button class="tab" *ngFor="let tab of tabs"
                    [routerLink]="tab.view"
                    routerLinkActive="active">
                {{ tab.label | translate }}
            </button>
        </div>

        <router-outlet></router-outlet>
    `,
    providers: [CommunicationRulesService],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class GroupsComponent implements OnInit, OnDestroy {

    // Subscribers
    private structureSubscriber: Subscription;

    // Tabs
    tabs = [
        {label: "ManualGroup", view: "manualGroup"},
        {label: "ProfileGroup", view: "profileGroup"},
        {label: "FunctionalGroup", view: "functionalGroup"},
        {label: "FunctionGroup", view: "functionGroup"}
    ];

    private routerSubscriber: Subscription;
    private error: Error;

    constructor(
        private route: ActivatedRoute,
        public router: Router,
        private cdRef: ChangeDetectorRef,
        public groupsStore: GroupsStore) {
    }

    ngOnInit(): void {
        // Watch selected structure
        this.structureSubscriber = routing.observe(this.route, "data").subscribe((data: Data) => {
            if (data['structure']) {
                this.groupsStore.structure = data['structure'];
                this.cdRef.markForCheck();
            }
        });

        this.routerSubscriber = this.router.events.subscribe(e => {
            if (e instanceof NavigationEnd) {
                this.cdRef.markForCheck();
            }
        });
    }

    ngOnDestroy(): void {
        this.structureSubscriber.unsubscribe();
        this.routerSubscriber.unsubscribe();
    }

    onError(error: Error) {
        console.error(error);
        this.error = error;
    }

    createButtonHidden() {
        return !this.router.isActive('/admin/' + this.groupsStore.structure.id + '/groups/manualGroup', false) 
            || this.router.isActive('/admin/' + this.groupsStore.structure.id + '/groups/manualGroup/create', true);
    }
}
