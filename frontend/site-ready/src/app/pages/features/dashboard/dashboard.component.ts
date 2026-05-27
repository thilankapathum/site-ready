import {Component, computed, OnInit, signal} from '@angular/core';
import {
  EngineerRankResponse,
  EngineerStatsResponse, PendingBreakdown,
  ReportResponse,
  STATUS_BADGE_CLASS,
  STATUS_LABELS
} from '../../../models/api.models';
import {AuthService} from '../../../services/auth/auth.service';
import {ReportService} from '../../../services/report.service';
import {Router, RouterLink} from '@angular/router';
import {forkJoin} from 'rxjs';
import {EngineerService} from '../../../services/engineer.service';

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

  engineerStats   = signal<EngineerStatsResponse | null>(null);
  engineerRank = signal<EngineerRankResponse | null>(null);
  engineerBreakdowns = signal<PendingBreakdown | null>(null)

  pendingCount    = signal(0);
  approvedCount   = signal(0);
  conditionallyApprovedCount   = signal(0);
  actionCount     = signal(0);
  totalCount = signal(0);

  stats = computed(() => [
    { label: 'Total Reports',  value: this.totalCount(),    color: 'text-base-content' },
    { label: 'Pending Review', value: this.pendingCount(),  color: 'text-info', sub: `Average Review time: ${this.engineerStats()?.avgReviewHours?.toFixed(1)} h` },
    { label: 'Pending Resubmission',  value: this.actionCount(),   color: 'text-warning' },
    { label: 'Approved',       value: this.approvedCount(), color: 'text-success' , sub: `Includes ${this.conditionallyApprovedCount()} Approved Conditionally`}
  ]);

  constructor(private auth: AuthService, private reportService: ReportService, private router: Router, private engineerService: EngineerService) {}

  ngOnInit(): void {

    if (this.role() === 'MANAGER') {
      this.router.navigate(['/manager']);
      return;
    }

    if (this.role() === 'ENGINEER') {
      this.loading.set(true);
      forkJoin({
        stats: this.engineerService.getEngineerStats(),
        rank:  this.engineerService.getRank(),
        breakdowns: this.engineerService.getBreakdowns(),
      }).subscribe({
        next: data => {
          this.engineerStats.set(data.stats);
          this.engineerRank.set(data.rank);
          this.engineerBreakdowns.set(data.breakdowns);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
    }

    const user = this.user();

    if (!user) return;

    const role   = user.role;
    const userId = user.userId;

    // Recent reports + total
    const recent$ = this.reportService.getMyReports(0, 6);

    // Status counts via search endpoint
    const pending$   = this.reportService.getReportCountByStatus('PENDING_REVIEW',         role, userId);
    const approved$  = this.reportService.getReportCountByStatus('APPROVED',                role, userId);
    const condApp$   = this.reportService.getReportCountByStatus('CONDITIONALLY_APPROVED',  role, userId);
    const actionNeed$ = this.reportService.getReportCountByStatus('RESUBMISSION_REQUIRED',  role, userId);

    forkJoin({ recent: recent$, pending: pending$, approved: approved$, condApp: condApp$, action: actionNeed$ })
      .subscribe({
        next: results => {
          this.reports.set(results.recent.content);
          this.totalCount.set(results.recent.totalElements ?? results.recent.page?.totalElements ?? 0);
          this.pendingCount.set(results.pending);
          this.approvedCount.set(results.approved + results.condApp);
          this.conditionallyApprovedCount.set(results.condApp);
          this.actionCount.set(results.action);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  statusBadge(s: string): string { return STATUS_BADGE_CLASS[s as keyof typeof STATUS_BADGE_CLASS] ?? 'badge'; }
  statusLabel(s: string): string { return STATUS_LABELS[s as keyof typeof STATUS_LABELS] ?? s; }
  formatDate(d: string): string { return new Date(d).toLocaleDateString('en-GB', { day:'2-digit', month:'short', year:'numeric' }); }

  openReport(r: ReportResponse): void {
    this.router.navigate(['/reports', r.id]);
  }

  navigateToReport(reportId:string): void {
    console.log('reportId', reportId)
    this.router.navigate(['/reports', reportId]);
  }

  formatHours(hours: number): string {
    if (hours === null || hours === undefined) return '—';
    if (hours < 1) return `${Math.round(hours * 60)} m`;
    if (hours < 24) return `${hours.toFixed(1)} h`;
    return `${(hours / 24).toFixed(1)} d`;
  }

  getGroupTotal(breakdowns: { label: string, count: number }[] | undefined): number {
    if (!breakdowns) return 0;
    return breakdowns.reduce((sum, item) => sum + item.count, 0);
  }

  getVersionColor(index: number): string {
    const colors = [
      '#4ea0ff','#135bf9', '#193ab7', '#162455',
      '#eff6ff', '#bddafe', '#0082ce', '#005889'
    ];
    return colors[index % colors.length];
  }

  getRatColor(index: number): string {
    const colors = [
      '#7af1a7', '#00c850', '#008033', '#0c532b',
      '#b9f14e', '#7acc00', '#487d00', '#33520c'
    ];
    return colors[index % colors.length];
  }

  getVendorColor(index: number): string {
    const colors = [
      '#fdcf2b', '#fa9700', '#b94b00', '#793205',
      '#ffb667', '#ff6700', '#c93400', '#7c2808'
    ];
    return colors[index % colors.length];
  }
}
