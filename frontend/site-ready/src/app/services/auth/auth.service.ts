import {computed, Injectable, signal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Router} from '@angular/router';
import {Observable, tap} from 'rxjs';
import {AuthResponse, UserRole} from '../../models/api.models';


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

  readonly currentUser = this._auth.asReadonly();
  readonly isLoggedIn  = computed(() => this._auth() !== null);
  readonly role        = computed(() => this._auth()?.role ?? null);
  readonly token       = computed(() => this._auth()?.token ?? null);

  constructor(private http: HttpClient, private router: Router) {}

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.BASE}/login`, { email, password }).pipe(
      tap(res => this.persist(res))
    );
  }

  register(payload: { email: string; password: string; fullName: string; company: string }): Observable<AuthResponse> {
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

  private persist(res: AuthResponse): void {
    if (!res.token) return;
    const stored: StoredAuth = {
      token: res.token,
      userId: res.userId,
      email: res.email,
      fullName: res.fullName,
      role: res.role,
    };
    localStorage.setItem(this.TOKEN_KEY, JSON.stringify(stored));
    this._auth.set(stored);
  }

  private loadFromStorage(): StoredAuth | null {
    try {
      const raw = localStorage.getItem(this.TOKEN_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }
}
