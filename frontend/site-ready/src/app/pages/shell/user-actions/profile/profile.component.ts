import {Component, signal} from '@angular/core';
import {AuthService} from '../../../../services/auth/auth.service';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-profile',
  imports: [
    FormsModule
  ],
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.css'
})
export class ProfileComponent {
  user = this.auth.currentUser;
  pw = { current: '', next: '', confirm: '' };
  pwLoading = signal(false);
  pwError   = signal('');
  pwSuccess = signal(false);

  constructor(private auth: AuthService) {}

  changePassword(): void {
    this.pwError.set(''); this.pwSuccess.set(false);
    if (!this.pw.current || !this.pw.next) { this.pwError.set('All fields required.'); return; }
    if (this.pw.next.length < 8) { this.pwError.set('Min. 8 characters.'); return; }
    if (this.pw.next !== this.pw.confirm) { this.pwError.set('Passwords do not match.'); return; }
    this.pwLoading.set(true);
    this.auth.changePassword(this.pw.current, this.pw.next).subscribe({
      next: () => { this.pwSuccess.set(true); this.pw = { current: '', next: '', confirm: '' }; this.pwLoading.set(false); },
      error: err => { this.pwError.set(err.error?.detail ?? 'Failed to update password.'); this.pwLoading.set(false); },
    });
  }
}
