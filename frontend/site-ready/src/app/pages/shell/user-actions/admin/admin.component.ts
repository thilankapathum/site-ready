import {Component, computed, OnInit, signal} from '@angular/core';
import {AuthResponse, CompanyRequest, CompanyResponse, CompanyType, UserRole} from '../../../../models/api.models';
import {AdminService} from '../../../../services/admin.service';
import {FormsModule} from '@angular/forms';
import {CompanyService} from '../../../../services/company.service';
import {ManagerService} from '../../../../services/manager.service';

@Component({
  selector: 'app-admin',
  imports: [
    FormsModule
  ],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.css'
})
export class AdminComponent implements OnInit {
  activeTab = signal<'users' | 'companies' | 'assignments'>('users');

  // Users
  users = signal<AuthResponse[]>([]);

  get pendingUsers(): number {
    return this.users().filter(u => !u.active).length;
  }

  // Companies
  companies = signal<CompanyResponse[]>([]);
  showForm = signal(false);
  editingCompany = signal<CompanyResponse | null>(null);
  saving = signal(false);
  formError = signal('');

  // Assignments State Variables
  managers = signal<AuthResponse[]>([]);
  allEngineers = signal<AuthResponse[]>([]);
  assignmentMap = signal<Map<string, Set<string>>>(new Map());
  savingAssignment = signal(false);

  // UX Optimization: Tracks the manager currently selected in the master list
  selectedManagerId = signal<string | null>(null);
  engineerSearchQuery = signal<string>('');

  // Computed properties to find the currently active manager context
  selectedManager = computed(() => {
    const id = this.selectedManagerId();
    return this.managers().find(m => m.userId === id) || null;
  });

  // Dynamic filter splitting engineers into two lists: Assigned vs Unassigned
  assignedEngineers = computed(() => {
    const managerId = this.selectedManagerId();
    if (!managerId) return [];
    const assignedSet = this.assignmentMap().get(managerId);

    return this.allEngineers().filter(eng =>
      assignedSet?.has(eng.userId) &&
      eng.fullName.toLowerCase().includes(this.engineerSearchQuery().toLowerCase())
    );
  });

  unassignedEngineers = computed(() => {
    const managerId = this.selectedManagerId();
    if (!managerId) return [];
    const assignedSet = this.assignmentMap().get(managerId);

    return this.allEngineers().filter(eng =>
      (!assignedSet || !assignedSet.has(eng.userId)) &&
      eng.fullName.toLowerCase().includes(this.engineerSearchQuery().toLowerCase())
    );
  });

  form: CompanyRequest & { country: string; contactEmail: string; notes: string } = {
    name: '', shortName: '', type: 'VENDOR', country: '', contactEmail: '', notes: ''
  };

  constructor(
    private adminService: AdminService,
    private companyService: CompanyService,
    private managerService: ManagerService
  ) {}

  ngOnInit(): void {
    this.loadUsers();
    this.loadCompanies();
    this.loadManagerData();
  }

  loadUsers(): void {
    this.adminService.listUsers().subscribe(u => this.users.set(u));
  }

  loadCompanies(): void {
    this.companyService.listAll().subscribe(c => this.companies.set(c));
  }

  // ... [Keep existing User and Company actions unchanged] ...

  changeRole(user: AuthResponse, role: UserRole): void {
    this.adminService.setRole(user.userId, role).subscribe({
      next: updated => this.users.update(list =>
        list.map(u => u.userId === updated.userId ? {...u, role: updated.role} : u)
      ),
    });
  }

  changeCompany(user: AuthResponse, companyId: string): void {
    this.adminService.setCompany(user.userId, companyId).subscribe({
      next: updated => this.users.update(list =>
        list.map(u => u.userId === updated.userId ? updated : u)
      ),
    });
  }

  toggleActive(user: AuthResponse, active: boolean): void {
    this.adminService.setActive(user.userId, active).subscribe({
      next: updated => this.users.update(list =>
        list.map(u => u.userId === updated.userId ? {...u, active: updated.active} : u)
      ),
    });
  }

  openCreateForm(): void {
    this.editingCompany.set(null);
    this.form = {name: '', shortName: '', type: 'VENDOR', country: '', contactEmail: '', notes: ''};
    this.formError.set('');
    this.showForm.set(true);
  }

  openEditForm(c: CompanyResponse): void {
    this.editingCompany.set(c);
    this.form = {
      name: c.name, shortName: c.shortName, type: c.type as CompanyType,
      country: c.country ?? '', contactEmail: c.contactEmail ?? '', notes: c.notes ?? ''
    };
    this.formError.set('');
    this.showForm.set(true);
  }

  closeForm(): void { this.showForm.set(false); this.editingCompany.set(null); }

  saveCompany(): void {
    if (!this.form.name || !this.form.shortName || !this.form.type) {
      this.formError.set('Name, short name, and type are required.'); return;
    }
    this.saving.set(true); this.formError.set('');
    const request: CompanyRequest = {
      name: this.form.name, shortName: this.form.shortName, type: this.form.type,
      country: this.form.country || undefined,
      contactEmail: this.form.contactEmail || undefined,
      notes: this.form.notes || undefined,
    };
    const op$ = this.editingCompany()
      ? this.companyService.update(this.editingCompany()!.id, request)
      : this.companyService.create(request);

    op$.subscribe({
      next: () => { this.loadCompanies(); this.closeForm(); this.saving.set(false); },
      error: err => { this.formError.set(err.error?.detail ?? 'Failed to save.'); this.saving.set(false); },
    });
  }

  toggleCompanyActive(c: CompanyResponse, active: boolean): void {
    this.companyService.setActive(c.id, active).subscribe({
      next: () => this.loadCompanies(),
    });
  }

  typeBadge(type: string): string {
    return type === 'VENDOR'     ? 'badge badge-info badge-sm badge-outline' :
      type === 'TELCO'      ? 'badge badge-primary badge-sm' :
        type === 'CONTRACTOR' ? 'badge badge-warning badge-sm badge-outline' :
          'badge badge-ghost badge-sm';
  }

  initials(name: string): string {
    return name.split(' ').map(n => n[0]).slice(0, 2).join('').toUpperCase();
  }


  loadManagerData(): void {
    this.managerService.getManagers().subscribe(mgrs => {
      this.managers.set(mgrs);

      // Auto-select the first manager to establish immediate context
      if (mgrs.length > 0 && !this.selectedManagerId()) {
        this.selectedManagerId.set(mgrs[0].userId);
      }

      const map = new Map<string, Set<string>>();
      let pending = mgrs.length;
      if (pending === 0) {
        this.assignmentMap.set(map);
        return;
      }
      for (const mgr of mgrs) {
        this.managerService.getEngineersForManager(mgr.userId).subscribe(engs => {
          map.set(mgr.userId, new Set(engs.map(e => e.userId)));
          pending--;
          if (pending === 0) this.assignmentMap.set(new Map(map));
        });
      }
    });

    this.adminService.listUsers().subscribe(users => {
      this.allEngineers.set(
        users.filter(u => u.role === 'ENGINEER' && u.active)
          .map(u => ({...u, companyName: u.companyName || u.message || 'Independent'}))
      );
    });
  }

  isAssigned(managerId: string, engineerId: string): boolean {
    return this.assignmentMap().get(managerId)?.has(engineerId) ?? false;
  }

  toggleAssignment(managerId: string, engineerId: string, isChecked: boolean): void {
    const map = new Map(this.assignmentMap());
    const set = new Set(map.get(managerId) ?? []);
    if (isChecked) set.add(engineerId); else set.delete(engineerId);
    map.set(managerId, set);
    this.assignmentMap.set(map);
  }

  saveAssignments(managerId: string): void {
    const engineerIds = [...(this.assignmentMap().get(managerId) ?? [])];
    this.savingAssignment.set(true);
    this.managerService.setEngineersForManager(managerId, engineerIds).subscribe({
      next: () => this.savingAssignment.set(false),
      error: () => this.savingAssignment.set(false),
    });
  }
}
