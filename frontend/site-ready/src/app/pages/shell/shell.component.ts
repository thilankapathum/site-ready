import {Component, computed, signal} from '@angular/core';
import {AuthService} from '../../services/auth/auth.service';
import {Router, RouterLink, RouterLinkActive, RouterOutlet} from '@angular/router';
import {CommonModule} from '@angular/common';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, CommonModule],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css'
})
export class ShellComponent {
  sidebarOpen = signal(true);

  constructor(public auth: AuthService) {}

  user    = this.auth.currentUser;
  role    = this.auth.role;
  isVendor   = computed(() => this.role() === 'VENDOR');
  isEngineer = computed(() => this.role() === 'ENGINEER');
  isAdmin    = computed(() => this.role() === 'ADMIN');

  initials = computed(() => {
    const name = this.user()?.fullName ?? '';
    return name.split(' ').map(n => n[0]).slice(0, 2).join('').toUpperCase();
  });

  roleBadge = computed(() => {
    const map: Record<string, string> = {
      VENDOR: 'Vendor', ENGINEER: 'Engineer', ADMIN: 'Administrator'
    };
    return map[this.role() ?? ''] ?? '';
  });

  toggleSidebar() { this.sidebarOpen.update(v => !v); }
}
