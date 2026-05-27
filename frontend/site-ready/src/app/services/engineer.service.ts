import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {EngineerRankResponse, EngineerStatsResponse, PendingBreakdown} from '../models/api.models';

@Injectable({
  providedIn: 'root'
})
export class EngineerService {

  private readonly BASE = '/api/engineer';

  constructor(private http: HttpClient) { }

  getEngineerStats(): Observable<EngineerStatsResponse> {
    return this.http.get<EngineerStatsResponse>(`${this.BASE}/stats`);
  }

  getRank(): Observable<EngineerRankResponse> {
    return this.http.get<EngineerRankResponse>(`${this.BASE}/rank`);
  }

  getBreakdowns(): Observable<PendingBreakdown> {
    return this.http.get<PendingBreakdown>(`${this.BASE}/breakdowns`);
  }
}
