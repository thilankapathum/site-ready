import {Component, computed, signal} from '@angular/core';
import {EngineerStatsResponse} from '../../../models/api.models';
import {ManagerService} from '../../../services/manager.service';

@Component({
  selector: 'app-manager-dashboard',
  imports: [],
  templateUrl: './manager-dashboard.component.html',
  styleUrl: './manager-dashboard.component.css'
})
export class ManagerDashboardComponent {
  stats   = signal<EngineerStatsResponse[]>([]);
  loading = signal(true);

  summaryStats = computed(() => {
    const s = this.stats();
    const total   = s.reduce((a, e) => a + e.totalReports, 0);
    const pending = s.reduce((a, e) => a + e.pendingReview, 0);
    const approved = s.reduce((a, e) => a + e.totalApproved, 0);
    const resub   = s.reduce((a, e) => a + e.resubmissionRequired, 0);
    return [
      { label: 'Total Reports',   value: total,   color: 'text-base-content', sub: `${s.length} engineers` },
      { label: 'Pending Review',  value: pending,  color: 'text-info',         sub: null },
      { label: 'Approved',        value: approved, color: 'text-success',      sub: null },
      { label: 'Resubmission',    value: resub,    color: 'text-warning',      sub: null },
    ];
  });

  teamTotal   = computed(() => this.stats().reduce((a, e) => a + e.totalReports, 0));
  teamPending = computed(() => this.stats().reduce((a, e) => a + e.pendingReview, 0));
  teamApproved = computed(() => this.stats().reduce((a, e) => a + e.totalApproved, 0));

  constructor(private managerService: ManagerService) {}

  ngOnInit(): void {
    this.managerService.getEngineerStats().subscribe({
      next: s => { this.stats.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  formatHours(hours: number): string {
    if (hours < 1) return `${Math.round(hours * 60)}m`;
    if (hours < 24) return `${hours.toFixed(1)}h`;
    return `${(hours / 24).toFixed(1)}d`;
  }

  performanceLabel(e: EngineerStatsResponse): string {
    if (e.totalReports === 0) return '—';
    if (e.longestPendingDays !== null && e.longestPendingDays > 7) return '🔴';
    if (e.pendingReview === 0 && e.totalReports > 0) return '🟢';
    if (e.avgReviewHours !== null && e.avgReviewHours > 48) return '🟡';
    return '🟢';
  }
}
