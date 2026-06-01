import {Component, computed, DestroyRef, inject, signal} from '@angular/core';
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
  sidebarOpen  = signal<boolean>(this.loadSidebarState());

  // FIX: Immediately determine sizes to avoid vanishing state on reload
  isMobile     = signal<boolean>(window.innerWidth < 1024);
  mobileHidden = signal<boolean>(window.innerWidth < 1024);

  constructor(public auth: AuthService) {
    const destroyRef = inject(DestroyRef);

    const handleResize = () => {
      const width = window.innerWidth;
      this.isMobile.set(width < 1024);
      if (width >= 1024) {
        this.mobileHidden.set(false);
      }
    };

    window.addEventListener('resize', handleResize);

    // Clean up event listeners automatically to prevent memory leaks
    destroyRef.onDestroy(() => {
      window.removeEventListener('resize', handleResize);
    });
  }

  user       = this.auth.currentUser;
  role       = this.auth.role;
  isVendor   = computed(() => this.role() === 'VENDOR');
  isEngineer = computed(() => this.role() === 'ENGINEER');
  isManager  = computed(() => this.role() === 'MANAGER');
  isAdmin    = computed(() => this.role() === 'ADMIN');

  initials = computed(() => {
    const name = this.user()?.fullName ?? '';
    return name.split(' ').map(n => n[0]).slice(0, 2).join('').toUpperCase();
  });

  roleBadge = computed(() => {
    const map: Record<string, string> = {
      VENDOR: 'Vendor', ENGINEER: 'Engineer', ADMIN: 'Administrator', MANAGER: 'Manager'
    };
    return map[this.role() ?? ''] ?? '';
  });

  toggleSidebar(): void {
    const next = !this.sidebarOpen();
    this.sidebarOpen.set(next);
    localStorage.setItem('ssv_sidebar', next ? '1' : '0');
  }

  toggleMobile(): void {
    this.mobileHidden.update(v => !v);
  }

  private loadSidebarState(): boolean {
    return localStorage.getItem('ssv_sidebar') !== '0';
  }
}
