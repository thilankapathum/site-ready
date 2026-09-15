import {computed, inject, Injectable, signal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Router} from '@angular/router';
import {Observable, tap} from 'rxjs';
import {AuthResponse, UserRole} from '../../models/api.models';
import {SessionExpiredService} from './session-expired.service';


interface StoredAuth {
  token: string;
  userId: string;
  email: string;
  fullName: string;
  role: UserRole;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly BASE = '/api/auth';
  private readonly TOKEN_KEY = 'ssv_auth';

  // Reactive auth state using Angular 19 signals
  private _auth = signal<StoredAuth | null>(this.loadFromStorage());
  private expiryTimer: ReturnType<typeof setTimeout> | null = null;

  readonly currentUser = this._auth.asReadonly();
  readonly isLoggedIn  = computed(() => this._auth() !== null);
  readonly role        = computed(() => this._auth()?.role ?? null);
  readonly token       = computed(() => this._auth()?.token ?? null);

  constructor(private http: HttpClient, private router: Router,private sessionExpired: SessionExpiredService) {
    // schedule on startup if token already exists (page refresh case)
    const stored = this.loadFromStorage();
    if (stored) this.scheduleExpiryWarning(stored.token);
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.BASE}/login`, { email, password }).pipe(
      tap(res => this.persist(res))
    );
  }

  register(payload: { email: string; password: string; fullName: string; companyId: string }): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.BASE}/register`, payload);
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.BASE}/change-password`, { currentPassword, newPassword });
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    this._auth.set(null);
    this.router.navigate(['/login']);
  }

  hasRole(...roles: UserRole[]): boolean {
    const r = this.role();
    return r !== null && roles.includes(r);
  }

  private loadFromStorage(): StoredAuth | null {
    try {
      const raw = localStorage.getItem(this.TOKEN_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }

  private persist(res: AuthResponse): void {
    if (!res.token) return;
    const stored: StoredAuth = {
      token: res.token,
      userId: res.userId,
      email: res.email,
      fullName: res.fullName,
      role: res.role,
    };  // existing
    localStorage.setItem(this.TOKEN_KEY, JSON.stringify(stored));
    this._auth.set(stored);
    this.scheduleExpiryWarning(res.token);  // ← add this
  }

  private scheduleExpiryWarning(token: string): void {
    // Decode JWT payload to get expiry time (no library needed — just base64)
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const expiresAt = payload.exp * 1000; // convert to ms
      const now = Date.now();
      const msUntilExpiry = expiresAt - now;
      // Show modal 60 seconds before expiry so user has time to react
      const msUntilWarning = msUntilExpiry - 60_000;

      if (this.expiryTimer) clearTimeout(this.expiryTimer);

      if (msUntilExpiry <= 0) {
        // Token is already expired (e.g. stale bookmark/idle tab reopened) — don't
        // wait for a failed API call to discover this.
        if (this.isLoggedIn()) {
          this.clearSession();
          this.sessionExpired.show();
        }
      } else if (msUntilWarning > 0) {
        this.expiryTimer = setTimeout(() => {
          if (this.isLoggedIn()) {
            this.clearSession();
            // FIX: Use the constructor-injected service instead of inject()
            this.sessionExpired.show();
          }
        }, msUntilWarning);
      }
    } catch {
      // If decode fails, fall back to reactive 401 detection
    }
  }

  clearSession(): void {
    if (this.expiryTimer) {
      clearTimeout(this.expiryTimer);
      this.expiryTimer = null;
    }
    localStorage.removeItem(this.TOKEN_KEY);
    this._auth.set(null);
  }
}
