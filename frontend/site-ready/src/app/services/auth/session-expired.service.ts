import {Injectable, signal} from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class SessionExpiredService {

  // True when a 401 has been received and the modal should show
  readonly isExpired = signal(false);

  show(): void {
    this.isExpired.set(true);
  }

  dismiss(): void {
    this.isExpired.set(false);
  }
}
