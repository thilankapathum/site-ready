import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {CompanyRequest, CompanyResponse} from '../models/api.models';

@Injectable({
  providedIn: 'root'
})
export class CompanyService {

  private readonly BASE = '/api/companies';

  constructor(private http: HttpClient) {}

  listAll(): Observable<CompanyResponse[]> {
    return this.http.get<CompanyResponse[]>(this.BASE);
  }

  listActive(): Observable<CompanyResponse[]> {
    return this.http.get<CompanyResponse[]>(`${this.BASE}/active`);
  }

  listVendors(): Observable<CompanyResponse[]> {
    return this.http.get<CompanyResponse[]>(`${this.BASE}/vendors`);
  }

  create(request: CompanyRequest): Observable<CompanyResponse> {
    return this.http.post<CompanyResponse>(this.BASE, request);
  }

  update(id: string, request: CompanyRequest): Observable<CompanyResponse> {
    return this.http.put<CompanyResponse>(`${this.BASE}/${id}`, request);
  }

  setActive(id: string, active: boolean): Observable<CompanyResponse> {
    return this.http.patch<CompanyResponse>(`${this.BASE}/${id}/active`, null, {
      params: { active: String(active) }
    });
  }
}
