import { Injectable } from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';
import {PendingBreakdown} from '../models/api.models';

@Injectable({
  providedIn: 'root'
})
export class VendorService {
  private readonly BASE = '/api/vendor';

  constructor(private http: HttpClient) { }

  getBreakdowns(): Observable<PendingBreakdown> {
    return this.http.get<PendingBreakdown>(`${this.BASE}/breakdowns`);
  }
}
