import {Component, signal} from '@angular/core';
import {ReportResponse, STATUS_BADGE_CLASS, STATUS_LABELS} from '../../../models/api.models';
import {ReportService} from '../../../services/report.service';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-review-list',
  imports: [
    RouterLink
  ],
  templateUrl: './review-list.component.html',
  styleUrl: './review-list.component.css'
})
export class ReviewListComponent {
  reports  = signal<ReportResponse[]>([]);
  loading  = signal(true);
  filter   = signal<string>('all');

  filtered = () => {
    const f = this.filter();
    const rs = this.reports();
    return f === 'all' ? rs : rs.filter(r => r.currentStatus === f);
  };

  pendingCount = () => this.reports().filter(r => r.currentStatus === 'PENDING_REVIEW').length;

  constructor(private reportService: ReportService) {}

  ngOnInit(): void {
    this.reportService.getMyReports(0, 200).subscribe({
      next: page => { this.reports.set(page.content); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  badge(s: string): string { return STATUS_BADGE_CLASS[s as keyof typeof STATUS_BADGE_CLASS] ?? 'badge'; }
  label(s: string): string { return STATUS_LABELS[s as keyof typeof STATUS_LABELS] ?? s; }
  formatDate(d: string): string { return new Date(d).toLocaleDateString('en-GB', { day:'2-digit', month:'short', year:'numeric' }); }
}
