import { Routes } from '@angular/router';
import {authGuard} from './services/auth/auth.guard';
import {roleGuard} from './services/auth/role.guard';

export const routes: Routes =  [
  {
    path: '',
    redirectTo: 'dashboard',
    pathMatch: 'full',
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./pages/shell/user-actions/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./pages/shell/user-actions/register/register.component').then(m => m.RegisterComponent),
  },
  {
    // Public — opened from the QR code on stamped documents
    path: 'verify/:id',
    loadComponent: () =>
      import('./pages/features/verify/verify.component').then(m => m.VerifyComponent),
  },
  {
    path: '',
    loadComponent: () =>
      import('./pages/shell/shell.component').then(m => m.ShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./pages/features/dashboard/dashboard.component').then(m => m.DashboardComponent),
      },
      {
        path: 'upload',
        loadComponent: () =>
          import('./pages/features/upload/upload.component').then(m => m.UploadComponent),
        canActivate: [roleGuard],
        data: { roles: ['ROLE_VENDOR', 'ROLE_VENDOR_MANAGER'] },
      },
      {
        path: 'review',
        loadComponent: () =>
          import('./pages/features/review-list/review-list.component').then(m => m.ReviewListComponent),
        canActivate: [roleGuard],
        data: { roles: ['ROLE_ENGINEER', 'ROLE_ADMIN', 'ROLE_MANAGER'] },
      },
      {
        path: 'review/:id',
        loadComponent: () =>
          import('./pages/features/review-detail/review-detail.component').then(m => m.ReviewDetailComponent),
        canActivate: [roleGuard],
        data: { roles: ['ROLE_ENGINEER', 'ROLE_ADMIN'] },
      },
      {
        path: 'reports/:id',
        loadComponent: () =>
          import('./pages/features/report-detail/report-detail.component')
            .then(m => m.ReportDetailComponent),
      },
      {
        path: 'search',
        loadComponent: () =>
          import('./pages/features/search/search.component').then(m => m.SearchComponent),
      },
      {
        path: 'admin',
        loadComponent: () =>
          import('./pages/shell/user-actions/admin/admin.component').then(m => m.AdminComponent),
        canActivate: [roleGuard],
        data: { roles: ['ROLE_ADMIN'] },
      },
      {
        path: 'manager',
        loadComponent: () =>
          import('./pages/features/manager-dashboard/manager-dashboard.component')
            .then(m => m.ManagerDashboardComponent),
        canActivate: [roleGuard],
        data: { roles: ['ROLE_MANAGER', 'ROLE_ADMIN'] },
      },
      {
        path: 'vendor-team',
        loadComponent: () =>
          import('./pages/features/vendor-manager-dashboard/vendor-manager-dashboard.component')
            .then(m => m.VendorManagerDashboardComponent),
        canActivate: [roleGuard],
        data: { roles: ['ROLE_VENDOR_MANAGER'] },
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./pages/shell/user-actions/profile/profile.component').then(m => m.ProfileComponent),
      },
    ],
  },
  {
    path: '**',
    redirectTo: 'dashboard',
  },
];
