import {Component, computed, OnInit, signal} from '@angular/core';
import {EngineerPendingResponse, VendorStatsResponse} from '../../../models/api.models';
import {forkJoin} from 'rxjs';
import {VendorService} from '../../../services/vendor.service';
import {RouterLink} from '@angular/router';
import {AuthService} from '../../../services/auth/auth.service';

@Component({
  selector: 'app-vendor-manager-dashboard',
  imports: [
    RouterLink,
  ],
  templateUrl: './vendor-manager-dashboard.component.html',
})
export class VendorManagerDashboardComponent implements OnInit {
  user = this.auth.currentUser;

  stats   = signal<VendorStatsResponse[]>([]);
  loading = signal(true);

  summaryStats = computed(() => {
    const s = this.stats();
    const total    = s.reduce((a, e) => a + e.totalReports, 0);
    const pending  = s.reduce((a, e) => a + e.pendingReview, 0);
    const approved = s.reduce((a, e) => a + e.totalApproved, 0);
    const condApproved = s.reduce((a, e) => a + e.conditionallyApproved, 0);
    const resub    = s.reduce((a, e) => a + e.resubmissionRequired, 0);
    return [
      { label: 'Total Reports',        value: total,    color: 'text-base-content', sub: `${s.length} team members` },
      { label: 'Pending Review',       value: pending,  color: 'text-info',         sub: null },
      { label: 'Pending Resubmission', value: resub,    color: 'text-warning',      sub: null },
      { label: 'Approved',             value: approved, color: 'text-success',      sub: `Including ${condApproved} Conditionally Approved` },
    ];
  });

  teamTotal = computed(() => this.stats().reduce((a, e) => a + e.totalReports, 0));
  teamResub = computed(() => this.stats().reduce((a, e) => a + e.resubmissionRequired, 0));
  teamApproved = computed(() => this.stats().reduce((a, e) => a + e.totalApproved, 0));

  reviewPending = signal<EngineerPendingResponse[]>([]);
  pendingTotal  = computed(() => this.reviewPending().reduce((a, e) => a + e.pendingCount, 0));
  maxPending    = computed(() => Math.max(1, ...this.reviewPending().map(e => e.pendingCount)));
  engineerCount = computed(() => this.reviewPending().filter(e => e.engineerId !== null).length);

  constructor(private vendorService: VendorService, private auth: AuthService) {}

  ngOnInit(): void {
    forkJoin({
      stats: this.vendorService.getTeamStats(),
      pending: this.vendorService.getReviewPending(),
    }).subscribe({
      next: d => {
        this.stats.set(d.stats);
        this.reviewPending.set(d.pending);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  formatHours(hours: number): string {
    if (hours < 1) return `${Math.round(hours * 60)}m`;
    if (hours < 24) return `${hours.toFixed(1)}h`;
    return `${(hours / 24).toFixed(1)}d`;
  }

  performanceLabel(e: VendorStatsResponse): string {
    if (e.totalReports === 0) return '—';
    if (e.oldestResubmissionDays !== null && e.oldestResubmissionDays > 28) return 'bg-error';
    if (e.resubmissionRequired === 0) return 'bg-success';
    if (e.avgResubmissionHours !== null && e.avgResubmissionHours > 72) return 'bg-warning';
    return 'bg-info';
  }
}
