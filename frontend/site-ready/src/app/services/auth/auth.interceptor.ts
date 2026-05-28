import {HttpHandlerFn, HttpInterceptorFn, HttpRequest} from '@angular/common/http';
import {inject} from '@angular/core';
import {AuthService} from './auth.service';
import {Router} from '@angular/router';
import {catchError, throwError} from 'rxjs';
import {SessionExpiredService} from './session-expired.service';

export const authInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
) => {
  const auth   = inject(AuthService);
  const sessionExpired = inject(SessionExpiredService);
  const router = inject(Router);
  const token  = auth.token();

  const authReq = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authReq).pipe(
    catchError(err => {
      if (err.status === 401) {
        // Don't show the modal for login requests themselves
        if (!req.url.includes('/auth/login')) {
          auth.clearSession();          // clear token without navigating
          sessionExpired.show();        // trigger the modal
        }
      }
      return throwError(() => err);
    })
  );
};
