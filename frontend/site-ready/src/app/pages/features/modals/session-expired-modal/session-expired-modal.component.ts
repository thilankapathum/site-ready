import {Component, effect, inject, OnDestroy, OnInit, signal} from '@angular/core';
import {SessionExpiredService} from '../../../../services/auth/session-expired.service';
import {AuthService} from '../../../../services/auth/auth.service';
import {Router} from '@angular/router';

@Component({
  selector: 'app-session-expired-modal',
  imports: [],
  templateUrl: './session-expired-modal.component.html',
  styleUrl: './session-expired-modal.component.css'
})
export class SessionExpiredModalComponent implements OnInit, OnDestroy {

  readonly sessionExpired = inject(SessionExpiredService);
  private  readonly auth  = inject(AuthService);
  private  readonly router = inject(Router);

  readonly totalSeconds = 8;
  countdown = signal(this.totalSeconds);

  private timer: ReturnType<typeof setInterval> | null = null;
  private currentUrl = '';

  constructor() {
    // Angular effects safely track signals and run whenever they change
    effect(() => {
      const isExpired = this.sessionExpired.isExpired();

      if (isExpired && this.timer === null) {
        this.currentUrl = this.router.url;
        this.startCountdown();
      }
    });
  }

  ngOnInit(): void {
    // Watch for session expiry and start countdown when it triggers
    // We use an effect-like pattern by checking in the interval
    this.startWatching();
  }

  ngOnDestroy(): void {
    this.clearTimer();
  }

  private startWatching(): void {
    // Poll the signal — Angular effect() would be cleaner but
    // this is safe and simple for a modal that appears rarely
    const poll = setInterval(() => {
      if (this.sessionExpired.isExpired() && this.timer === null) {
        // Capture where the user was
        this.currentUrl = this.router.url;
        this.startCountdown();
        clearInterval(poll);
      }
    }, 200);
  }

  private startCountdown(): void {
    this.countdown.set(this.totalSeconds);
    this.timer = setInterval(() => {
      const next = this.countdown() - 1;
      this.countdown.set(next);
      if (next <= 0) {
        this.goToLogin();
      }
    }, 1000);
  }

  private clearTimer(): void {
    if (this.timer !== null) {
      clearInterval(this.timer);
      this.timer = null;
    }
  }

  goToLogin(): void {
    this.clearTimer();
    this.sessionExpired.dismiss();
    this.countdown.set(this.totalSeconds);

    this.router.navigate(['/login'], {
      queryParams: this.currentUrl && this.currentUrl !== '/login'
        ? { returnUrl: this.currentUrl }
        : {}
    });
  }
}
