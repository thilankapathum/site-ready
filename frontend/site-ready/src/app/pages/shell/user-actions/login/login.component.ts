import {Component, signal} from '@angular/core';
import {AuthService} from '../../../../services/auth/auth.service';
import {Router, RouterLink} from '@angular/router';
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
  email    = '';
  password = '';
  loading  = signal(false);
  error    = signal('');
  showPassword = signal(false);

  constructor(private auth: AuthService, private router: Router) {
    if (this.auth.isLoggedIn()) this.router.navigate(['/dashboard']);
  }

  login(): void {
    this.error.set('');
    if (!this.email || !this.password) {
      this.error.set('Please enter your email and password.');
      return;
    }
    this.loading.set(true);
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: err => {
        if (err.status === 403) {
          this.error.set(err.error?.detail ?? 'Your account is not activated. Contact the system administrator.');
        } else if (err.status === 401) {
          this.error.set('Invalid email or password.');
        } else {
          this.error.set(err.error?.detail ?? 'Login failed. Please try again.');
        }
        this.loading.set(false);
      },
    });
  }
}
