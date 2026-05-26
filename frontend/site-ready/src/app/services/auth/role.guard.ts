import {ActivatedRouteSnapshot, CanActivateFn, Router} from '@angular/router';
import {inject} from '@angular/core';
import {AuthService} from './auth.service';

export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot) => {
  const auth   = inject(AuthService);
  const router = inject(Router);

  const map: Record<string, string> = {
    VENDOR: 'ROLE_VENDOR', ENGINEER: 'ROLE_ENGINEER',
    MANAGER: 'ROLE_MANAGER', ADMIN: 'ROLE_ADMIN'
  };

  const requiredRoles: string[] = route.data['roles'] ?? [];
  const userRole = auth.role();

  // Map stored role ('VENDOR') to Spring Security authority ('ROLE_VENDOR')
  const userAuthority = userRole ? `ROLE_${userRole}` : null;

  if (userAuthority && requiredRoles.includes(userAuthority)) return true;

  // ADMIN can access everything
  if (userRole === 'ADMIN') return true;

  router.navigate(['/dashboard']);
  return false;
};
