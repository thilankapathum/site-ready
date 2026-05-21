import {Component, computed, OnInit, signal} from '@angular/core';
import {AuthResponse, CompanyRequest, CompanyResponse, CompanyType, UserRole} from '../../../../models/api.models';
import {AdminService} from '../../../../services/admin.service';
import {FormsModule} from '@angular/forms';
import {CompanyService} from '../../../../services/company.service';

@Component({
  selector: 'app-admin',
  imports: [
    FormsModule
  ],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.css'
})
export class AdminComponent implements OnInit {
  activeTab = signal<'users' | 'companies'>('users');

  // Users
  users   = signal<AuthResponse[]>([]);

  get pendingUsers(): number {
    return this.users().filter(u => !u.active).length;
  }

  // Companies
  companies      = signal<CompanyResponse[]>([]);
  showForm       = signal(false);
  editingCompany = signal<CompanyResponse | null>(null);
  saving         = signal(false);
  formError      = signal('');

  form: CompanyRequest & { country: string; contactEmail: string; notes: string } = {
    name: '', shortName: '', type: 'VENDOR', country: '', contactEmail: '', notes: ''
  };

  constructor(
    private adminService: AdminService,
    private companyService: CompanyService
  ) {}

  ngOnInit(): void {
    this.loadUsers();
    this.loadCompanies();
  }

  loadUsers(): void {
    this.adminService.listUsers().subscribe(u => this.users.set(u));
  }

  loadCompanies(): void {
    this.companyService.listAll().subscribe(c => this.companies.set(c));
  }

  // ── User actions ──

  changeRole(user: AuthResponse, role: UserRole): void {
    this.adminService.setRole(user.userId, role).subscribe({
      next: updated => this.users.update(list =>
        list.map(u => u.userId === updated.userId ? { ...u, role: updated.role } : u)
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
        list.map(u => u.userId === updated.userId ? { ...u, active: updated.active } : u)
      ),
    });
  }

  // ── Company actions ──

  openCreateForm(): void {
    this.editingCompany.set(null);
    this.form = { name: '', shortName: '', type: 'VENDOR', country: '', contactEmail: '', notes: '' };
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
}
