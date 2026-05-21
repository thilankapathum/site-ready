import {Component, OnInit, signal} from '@angular/core';
import {AuthService} from '../../../../services/auth/auth.service';
import {Router, RouterLink} from '@angular/router';
import {FormsModule} from '@angular/forms';
import {CompanyResponse} from '../../../../models/api.models';
import {CompanyService} from '../../../../services/company.service';

@Component({
  selector: 'app-register',
  imports: [
    FormsModule,
    RouterLink
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent implements OnInit {
  form = { fullName: '', companyId: '', email: '', password: '', confirmPassword: '' };
  loading = signal(false);
  error   = signal('');
  success = signal(false);
  showPass = signal(false);

  companies = signal<CompanyResponse[]>([]);

  constructor(private auth: AuthService, private router: Router, private companyService: CompanyService) {}

  ngOnInit(): void {
    this.companyService.listActive().subscribe(c => this.companies.set(c));
  }

  register(): void {
    this.error.set('');
    const { fullName, companyId, email, password, confirmPassword } = this.form;
    if (!fullName || !companyId || !email || !password) {
      this.error.set('All fields are required.'); return;
    }
    if (password.length < 8) {
      this.error.set('Password must be at least 8 characters.'); return;
    }
    if (password !== confirmPassword) {
      this.error.set('Passwords do not match.'); return;
    }
    this.loading.set(true);
    this.auth.register({ email, password, fullName, companyId }).subscribe({
      next: () => { this.success.set(true); this.loading.set(false); },
      error: err => {
        this.error.set(err.error?.detail ?? 'Registration failed. Please try again.');
        this.loading.set(false);
      },
    });
  }
}
