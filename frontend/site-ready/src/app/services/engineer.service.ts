import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {EngineerStatsResponse} from '../models/api.models';

@Injectable({
  providedIn: 'root'
})
export class EngineerService {

  private readonly BASE = '/api/engineer';

  constructor(private http: HttpClient) { }

  getEngineerStats(): Observable<EngineerStatsResponse> {
    return this.http.get<EngineerStatsResponse>(`${this.BASE}/stats`);
  }
}
