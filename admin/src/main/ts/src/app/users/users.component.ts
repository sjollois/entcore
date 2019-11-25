import {ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import {ActivatedRoute, Data, NavigationEnd, Router} from '@angular/router';
import {Subscription} from 'rxjs';

import {StructureModel} from '../core/store';
import {routing} from '../core/services/routing.service';
import {SpinnerService, UserlistFiltersService, UserListService} from '../core/services';
import {UsersStore} from './users.store';

@Component({
    selector: 'ode-users-root',
    templateUrl: './users.component.html',
    providers: [UsersStore, UserListService],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class UsersComponent implements OnInit, OnDestroy {

    constructor(
        private route: ActivatedRoute,
        public router: Router,
        private cdRef: ChangeDetectorRef,
        public usersStore: UsersStore,
        private listFilters: UserlistFiltersService,
        private spinner: SpinnerService) {
    }

    private dataSubscriber: Subscription;
    private routerSubscriber: Subscription;

    ngOnInit(): void {
        this.dataSubscriber = routing.observe(this.route, 'data').subscribe((data: Data) => {
            if (data.structure) {
                const structure: StructureModel = data.structure;
                this.usersStore.structure = structure;
                this.initFilters(structure);
                this.cdRef.detectChanges();
            }
        });

        this.routerSubscriber = this.router.events.subscribe(e => {
            if (e instanceof NavigationEnd) {
                this.cdRef.markForCheck();
            }
        });
    }

    ngOnDestroy(): void {
        this.dataSubscriber.unsubscribe();
        this.routerSubscriber.unsubscribe();
    }

    closeCompanion() {
        this.router.navigate(['../users'], {relativeTo: this.route}).then(() => {
            this.usersStore.user = null;
        });
    }

    openUserDetail(user) {
        this.usersStore.user = user;
        this.spinner.perform('portal-content', this.router.navigate([user.id, 'details'], {relativeTo: this.route}));
    }

    openCompanionView(view) {
        this.router.navigate([view], {relativeTo: this.route});
    }

    private initFilters(structure: StructureModel) {
        this.listFilters.resetFilters();

        this.listFilters.setClassesComboModel(structure.classes);
        this.listFilters.setSourcesComboModel(structure.sources);

        const aafFunctions: Array<Array<string>> = [];
        structure.aafFunctions.forEach(f => {
            f.forEach(f2 => aafFunctions.push([f2[2], f2[4]]));
        });
        this.listFilters.setFunctionsComboModel(aafFunctions);

        this.listFilters.setProfilesComboModel(structure.profiles.map(p => p.name));
        this.listFilters.setFunctionalGroupsComboModel(
            structure.groups.data.filter(g => g.type === 'FunctionalGroup').map(g => g.name));
        this.listFilters.setManualGroupsComboModel(
            structure.groups.data.filter(g => g.type === 'ManualGroup').map(g => g.name));
        this.listFilters.setMailsComboModel([]);
    }
}
