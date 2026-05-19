import {Component, computed, OnInit, signal} from '@angular/core';
import {AuthResponse, UserRole} from '../../../../models/api.models';
import {AdminService} from '../../../../services/admin.service';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-admin',
  imports: [
    FormsModule
  ],
  templateUrl: './admin.component.html',
  styleUrl: './admin.component.css'
})
export class AdminComponent implements OnInit {
  users   = signal<AuthResponse[]>([]);
  loading = signal(true);

  totalUsers  = computed(() => this.users().length);
  pendingUsers = computed(() => this.users().filter(u => !u.active).length);

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);
    this.adminService.listUsers().subscribe({
      next: users => { this.users.set(users); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  changeRole(user: AuthResponse, role: UserRole): void {
    this.adminService.setRole(user.userId, role).subscribe({
      next: updated => {
        this.users.update(list =>
          list.map(u => u.userId === updated.userId ? { ...u, role: updated.role } : u)
        );
      },
    });
  }

  toggleActive(user: AuthResponse, active: boolean): void {
    this.adminService.setActive(user.userId, active).subscribe({
      next: updated => {
        this.users.update(list =>
          list.map(u => u.userId === updated.userId ? { ...u, active: updated.active } : u)
        );
      },
    });
  }

  initials(name: string): string {
    return name.split(' ').map(n => n[0]).slice(0, 2).join('').toUpperCase();
  }
}
