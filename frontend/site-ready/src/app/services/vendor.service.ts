import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {EngineerPendingResponse, PendingBreakdown, VendorStatsResponse} from '../models/api.models';

@Injectable({
  providedIn: 'root'
})
export class VendorService {
  private readonly BASE = '/api/vendor';

  constructor(private http: HttpClient) { }

  getBreakdowns(): Observable<PendingBreakdown> {
    return this.http.get<PendingBreakdown>(`${this.BASE}/breakdowns`);
  }

  getTeamStats(): Observable<VendorStatsResponse[]> {
    return this.http.get<VendorStatsResponse[]>(`${this.BASE}/team-stats`);
  }

  getReviewPending(): Observable<EngineerPendingResponse[]> {
    return this.http.get<EngineerPendingResponse[]>(`${this.BASE}/review-pending`);
  }
}
