import { Component, OnInit, OnDestroy, ElementRef, ViewChild } from '@angular/core'
import { Router, ActivatedRoute, Data } from '@angular/router'

import { StructureModel, globalStore } from '../store'
import { Subscription } from 'rxjs/Subscription'
import { Config } from '../resolvers/Config';

@Component({
    selector: 'app-nav',
    template: `
        <portal>
            <div header-left>
                <i class="fa" aria-hidden="true"
                    [ngClass]="{'fa-times': openside, 'fa-bars': !openside, 'is-hidden': structures.length == 1 && !structures[0].children}"
                    (click)="openside = !openside"
                    #sidePanelOpener></i>
                <span class="link" [routerLink]="'/admin/' + currentStructure?.id">
                    {{ currentStructure?.name }}
                </span>
            </div>
            <div header-right>
                <i class="dashboard" aria-hidden="true"
                    *ngIf="currentStructure"
                    [title]="'nav.structure' | translate"
                    [routerLink]="'/admin/' + currentStructure?.id"
                    [class.active]="router.isActive('/admin/' + currentStructure?.id, true)">
                </i>
                <i class="school" aria-hidden="true"
                    *ngIf="currentStructure"
                    [title]="'management.structure' | translate"
                    [routerLink]="'/admin/' + currentStructure?.id + '/management/message-flash'"
                    [class.active]="router.isActive('/admin/' + currentStructure?.id + '/management', false)">
                </i>
                <i class="fa fa-user" aria-hidden="true"
                    *ngIf="currentStructure"
                    [title]="'users' | translate"
                    [routerLink]="'/admin/' + currentStructure?.id + '/users/filter'"
                    [class.active]="router.isActive('/admin/' + currentStructure?.id + '/users', false)">
                </i>
                <i class="fa fa-users"
                    *ngIf="currentStructure"
                    [title]="'groups' | translate"
                    [routerLink]="'/admin/' + currentStructure?.id + '/groups/manual'"
                    [class.active]="router.isActive('/admin/' + currentStructure?.id + '/groups', false)">
                </i>
                <i class="fa fa-th"
                    *ngIf="currentStructure"
                    [title]="'services' | translate"
                    [routerLink]="'/admin/' + currentStructure?.id + '/services/applications'"
                    [class.active]="router.isActive('/admin/' + currentStructure?.id + '/services', false)">
                </i>
                <i class="fa fa-exchange"
                    *ngIf="currentStructure"
                    [title]="'imports.exports' | translate"
                    [routerLink]="'/admin/' + currentStructure?.id + '/imports-exports/import-csv'"
                    [class.active]="router.isActive('/admin/' + currentStructure?.id + '/imports-exports', false)">
                </i>
                <i class="fa fa-exclamation-triangle"
                    *ngIf="currentStructure"
                    [title]="'reports' | translate"
                    (click)="openReports()">
                </i>
                <a href="/auth/logout"
                    [title]="'logout' | translate">
                    <i class="fa fa-power-off" aria-hidden="true"></i>
                </a>
                <a *ngIf="!config['hide-adminv1-link']"
                    class="old-console" href="/directory/admin-console"
                    [title]="'switch.old.admin.console.tooltip' | translate">
                    <i class="fa fa-step-backward"></i>
                </a>
            </div>
            <div section>
                <side-panel
                    [toggle]="openside"
                    (onClose)="openside = false"
                    [opener]="sidePanelOpener">
                    <div class="side-search">
                        <search-input (onChange)="structureFilter = $event"
                            [attr.placeholder]="'search.structure' | translate">
                        </search-input>
                    </div>
                    <item-tree
                        [items]="structures"
                        order="name"
                        display="name"
                        [flatten]="structureFilter && structureFilter.trim() ? ['children'] : []"
                        [filter]="{ name : structureFilter?.trim() }"
                        (onSelect)="currentStructure = $event; !currentStructure.children && openside = false;"
                        [lastSelected]="currentStructure"></item-tree>
                </side-panel>

                <spinner-cube waitingFor="portal-content" class="portal-spinner">
                </spinner-cube>
                
                <div class="welcome-message" *ngIf="router.url === '/admin' && !config['hide-adminv1-link']">
                    <s5l>message.new.console</s5l>
                    <a class="old-console" href="/directory/admin-console"
                        [title]="'switch.old.admin.console.tooltip' | translate">
                        <i class="fa fa-step-backward"></i>
                    </a>
                </div>

                <div class="portal-body">
                    <router-outlet></router-outlet>
                </div>
            </div>
        </portal>`
})
export class NavComponent implements OnInit, OnDestroy {
    private _currentStructure: StructureModel
    set currentStructure(struct: StructureModel){
        this._currentStructure = struct

        let replacerRegex = /^\/{0,1}admin(\/[^\/]+){0,1}/
        let newPath = window.location.pathname.replace(replacerRegex, `/admin/${struct.id}`)

        this.router.navigateByUrl(newPath)
    }
    get currentStructure(){ return this._currentStructure }
    openside: boolean
    structureFilter: String
    structures: StructureModel[]

    @ViewChild("sidePanelOpener") sidePanelOpener: ElementRef

    private structureSubscriber : Subscription;
    private routeSubscription: Subscription;

    public config: Config;

    constructor(
        public router: Router,
        private route: ActivatedRoute) {}

    ngOnInit() {
        this.structures = globalStore.structures.asTree();

        if (this.structures.length == 1 && !this.structures[0].children)
            this.currentStructure = this.structures[0];

        this.structureSubscriber = this.route.children[0].params.subscribe(params => {
            let structureId = params['structureId'];
            if(structureId) {
                this.currentStructure = globalStore.structures.data.find(
                    s => s.id === structureId);
            }
        });

        this.routeSubscription = this.route.data.subscribe((data: Data) => {
            this.config = data['config'];
        });
    }

    ngOnDestroy() {
        if (this.structureSubscriber) {
            this.structureSubscriber.unsubscribe();
        }
        if (this.routeSubscription) {
            this.routeSubscription.unsubscribe();
        }
    }

    public openReports(): void {
        window.open('/timeline/admin-history', '_blank');
    }
}
