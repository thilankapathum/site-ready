import { Injectable } from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {AuthResponse, UserRole} from '../models/api.models';

@Injectable({
  providedIn: 'root'
})
export class AdminService {

  private readonly BASE = '/api/admin';

  constructor(private http: HttpClient) {}

  listUsers(): Observable<AuthResponse[]> {
    return this.http.get<AuthResponse[]>(`${this.BASE}/users`);
  }

  setRole(userId: string, role: UserRole): Observable<AuthResponse> {
    return this.http.patch<AuthResponse>(`${this.BASE}/users/${userId}/role`, null, {
      params: new HttpParams().set('role', role),
    });
  }

  setActive(userId: string, active: boolean): Observable<AuthResponse> {
    return this.http.patch<AuthResponse>(`${this.BASE}/users/${userId}/activate`, null, {
      params: new HttpParams().set('active', String(active)),
    });
  }
}
