import {Component, signal} from '@angular/core';
import {AuthService} from '../../../../services/auth/auth.service';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-login',
  imports: [
    FormsModule,
    RouterLink
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  private returnUrl = '/dashboard';
  email = '';
  password = '';
  loading = signal(false);
  error = signal('');
  showPassword = signal(false);

  constructor(private auth: AuthService,
              private router: Router,
              private route: ActivatedRoute) {
    if (this.auth.isLoggedIn()) this.router.navigate(['/dashboard']);
    // Capture return URL from query params set by the session expired modal
    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/dashboard';
  }

  login(): void {
    this.error.set('');
    if (!this.email || !this.password) {
      this.error.set('Please enter your email and password.');
      return;
    }
    this.loading.set(true);
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigateByUrl(this.returnUrl),  // ← land back where they were
      error: err => {
        this.error.set(
          err.status === 403
            ? err.error?.detail ?? 'Account not activated. Contact administrator.'
            : err.status === 401
              ? 'Invalid email or password.'
              : err.error?.detail ?? 'Login failed. Please try again.'
        );
        this.loading.set(false);
      },
    });
  }
}
