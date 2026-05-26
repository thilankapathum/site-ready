import { Injectable } from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {AuthResponse, EngineerStatsResponse, PageResponse, ReportResponse} from '../models/api.models';
import {Observable} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class ManagerService {

  private readonly BASE = '/api/manager';
  private readonly ADMIN = '/api/admin';

  constructor(private http: HttpClient) { }

  getMyEngineers(): Observable<AuthResponse[]> {
    return this.http.get<AuthResponse[]>(`${this.BASE}/engineers`);
  }

  getEngineerStats(): Observable<EngineerStatsResponse[]> {
    return this.http.get<EngineerStatsResponse[]>(`${this.BASE}/stats`);
  }

  getQueue(page = 0, size = 20): Observable<PageResponse<ReportResponse>> {
    return this.http.get<PageResponse<ReportResponse>>(`${this.BASE}/queue`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  // Admin: get all managers
  getManagers(): Observable<AuthResponse[]> {
    return this.http.get<AuthResponse[]>(`${this.ADMIN}/managers`);
  }

  // Admin: get engineers assigned to a manager
  getEngineersForManager(managerId: string): Observable<AuthResponse[]> {
    return this.http.get<AuthResponse[]>(`${this.ADMIN}/managers/${managerId}/engineers`);
  }

  // Admin: set engineers for a manager (replaces all)
  setEngineersForManager(managerId: string, engineerIds: string[]): Observable<void> {
    return this.http.put<void>(`${this.ADMIN}/managers/${managerId}/engineers`, engineerIds);
  }
}
