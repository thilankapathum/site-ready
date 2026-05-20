import { Injectable } from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {Observable} from 'rxjs';
import {AuthResponse, PageResponse, ReportResponse, ReportStatus, VersionResponse} from '../models/api.models';

@Injectable({
  providedIn: 'root'
})
export class ReportService {

  private readonly BASE = '/api/reports';
  private readonly USERS = '/api/users';

  constructor(private http: HttpClient) {}

  uploadReport(formData: FormData): Observable<ReportResponse> {
    return this.http.post<ReportResponse>(`${this.BASE}/upload`, formData);
  }

  reviewReport(reportId: string, formData: FormData): Observable<ReportResponse> {
    return this.http.post<ReportResponse>(`${this.BASE}/${reportId}/review`, formData);
  }

  downloadPdf(reportId: string, version: number): Observable<Blob> {
    return this.http.get(`${this.BASE}/${reportId}/download/${version}`, {
      responseType: 'blob',
    });
  }

  getMyReports(page = 0, size = 20): Observable<PageResponse<ReportResponse>> {
    return this.http.get<PageResponse<ReportResponse>>(`${this.BASE}/my`, {
      params: new HttpParams().set('page', page).set('size', size),
    });
  }

  search(filters: {
    siteId?: string;
    project?: string;
    status?: ReportStatus | '';
    engineerId?: string;
    vendorId?: string;
    page?: number;
    size?: number;
  }): Observable<PageResponse<ReportResponse>> {
    let params = new HttpParams();
    if (filters.siteId)    params = params.set('siteId',     filters.siteId);
    if (filters.project)   params = params.set('project',    filters.project);
    if (filters.status)    params = params.set('status',     filters.status);
    if (filters.engineerId) params = params.set('engineerId', filters.engineerId);
    if (filters.vendorId)  params = params.set('vendorId',   filters.vendorId);
    params = params.set('page', filters.page ?? 0).set('size', filters.size ?? 20);
    return this.http.get<PageResponse<ReportResponse>>(`${this.BASE}/search`, { params });
  }

  exportExcel(filters: Record<string, string>): void {
    const params = new HttpParams({ fromObject: filters });
    window.open(`${this.BASE}/export/excel?${params.toString()}`, '_blank');
  }

  exportCsv(filters: Record<string, string>): void {
    const params = new HttpParams({ fromObject: filters });
    window.open(`${this.BASE}/export/csv?${params.toString()}`, '_blank');
  }

  getActiveEngineers(): Observable<AuthResponse[]> {
    return this.http.get<AuthResponse[]>(`${this.USERS}/engineers`);
  }

  getReport(reportId: string): Observable<ReportResponse> {
    return this.http.get<ReportResponse>(`${this.BASE}/${reportId}`);
  }

  getVersions(reportId: string): Observable<VersionResponse[]> {
    return this.http.get<VersionResponse[]>(`${this.BASE}/${reportId}/versions`);
  }

  assignEngineer(reportId: string, engineerId: string | null): Observable<ReportResponse> {
    let params = new HttpParams();
    if (engineerId) params = params.set('engineerId', engineerId);
    return this.http.patch<ReportResponse>(`${this.BASE}/${reportId}/assign-engineer`, null, { params });
  }

  downloadVersionPdf(reportId: string, versionId: string): Observable<Blob> {
    return this.http.get(`${this.BASE}/${reportId}/download-version/${versionId}`, {
      responseType: 'blob',
    });
  }

  downloadReviewedPdf(reportId: string, versionId: string): Observable<Blob> {
    return this.http.get(`${this.BASE}/${reportId}/download-version/${versionId}/reviewed`, {
      responseType: 'blob',
    });
  }
}
