import {Component, computed, OnInit, signal} from '@angular/core';
import {ReportResponse, STATUS_BADGE_CLASS, STATUS_LABELS} from '../../../models/api.models';
import {AuthService} from '../../../services/auth/auth.service';
import {ReportService} from '../../../services/report.service';
import {Router, RouterLink} from '@angular/router';

@Component({
  selector: 'app-dashboard',
  imports: [
    RouterLink
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent implements OnInit {
  user = this.auth.currentUser;
  role = this.auth.role;
  isVendor   = computed(() => this.role() === 'VENDOR');
  isEngineer = computed(() => this.role() === 'ENGINEER');
  isAdmin    = computed(() => this.role() === 'ADMIN');

  reports = signal<ReportResponse[]>([]);
  loading = signal(true);

  stats = computed(() => {
    const rs = this.reports();
    return [
      { label: 'Total',          value: rs.length,                                      color: 'text-base-content' },
      { label: 'Pending Review', value: rs.filter(r => r.currentStatus === 'PENDING_REVIEW').length,    color: 'text-info' },
      { label: 'Approved',       value: rs.filter(r => ['APPROVED','CONDITIONALLY_APPROVED'].includes(r.currentStatus)).length, color: 'text-success' },
      { label: 'Action Needed',  value: rs.filter(r => r.currentStatus === 'RESUBMISSION_REQUIRED').length, color: 'text-warning' },
    ];
  });

  constructor(private auth: AuthService, private reportService: ReportService, private router: Router) {}

  ngOnInit(): void {
    this.reportService.getMyReports(0, 50).subscribe({
      next: page => { this.reports.set(page.content); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  statusBadge(s: string): string { return STATUS_BADGE_CLASS[s as keyof typeof STATUS_BADGE_CLASS] ?? 'badge'; }
  statusLabel(s: string): string { return STATUS_LABELS[s as keyof typeof STATUS_LABELS] ?? s; }
  formatDate(d: string): string { return new Date(d).toLocaleDateString('en-GB', { day:'2-digit', month:'short', year:'numeric' }); }

  openReport(r: ReportResponse): void {
    this.router.navigate(['/reports', r.id]);
  }
}
