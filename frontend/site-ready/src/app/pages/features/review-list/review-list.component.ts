import {Component, computed, OnInit, signal} from '@angular/core';
import {ReportResponse, STATUS_BADGE_CLASS, STATUS_LABELS} from '../../../models/api.models';
import {ReportService} from '../../../services/report.service';
import {RouterLink} from '@angular/router';
import {forkJoin} from 'rxjs';

@Component({
  selector: 'app-review-list',
  imports: [
    RouterLink
  ],
  templateUrl: './review-list.component.html',
  styleUrl: './review-list.component.css'
})
export class ReviewListComponent implements OnInit {
  reports    = signal<ReportResponse[]>([]);
  loading    = signal(true);
  filter     = signal<string>('all');
  page       = signal(0);
  total      = signal(0);
  totalPages = signal(0);
  readonly pageSize = 5;

  pageNumbers = computed<number[]>(() => {
    const total = this.totalPages();
    const current = this.page();
    if (total <= 7) return Array.from({ length: total }, (_, i) => i);
    const pages: number[] = [];
    pages.push(0);
    if (current > 2) pages.push(-1);
    for (let i = Math.max(1, current - 1); i <= Math.min(total - 2, current + 1); i++) {
      pages.push(i);
    }
    if (current < total - 3) pages.push(-1);
    pages.push(total - 1);
    return pages;
  });

  constructor(private reportService: ReportService) {}

  ngOnInit(): void {
    this.load();
  }

  setFilter(f: string): void {
    this.filter.set(f);
    this.page.set(0);
    this.load();
  }

  load(): void {
    this.loading.set(true);

    const filter = this.filter();

    if (filter === 'APPROVED') {
      // Fetch both APPROVED and CONDITIONALLY_APPROVED in parallel then merge
      const approved$ = this.reportService.search({
        status: 'APPROVED' as any,
        page: this.page(),
        size: this.pageSize
      });
      const conditional$ = this.reportService.search({
        status: 'CONDITIONALLY_APPROVED' as any,
        page: 0,
        size: 200   // fetch all conditional approvals — typically few
      });

      forkJoin({ approved: approved$, conditional: conditional$ }).subscribe({
        next: results => {
          const approvedItems   = results.approved.content;
          const conditionalItems = results.conditional.content;

          // Merge and sort by updatedAt descending
          const merged = [...approvedItems, ...conditionalItems]
            .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());

          const total = (results.approved.totalElements ?? results.approved.page?.totalElements ?? 0)
            + (results.conditional.totalElements ?? results.conditional.page?.totalElements ?? 0);

          this.reports.set(merged);
          this.total.set(total);
          // Pagination based on primary approved page (conditional fetched all at once)
          this.totalPages.set(results.approved.totalPages ?? results.approved.page?.totalPages ?? 1);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });

    } else {
      const request$ = filter === 'all'
        ? this.reportService.getMyReports(this.page(), this.pageSize)
        : this.reportService.search({
          status: filter as any,
          page: this.page(),
          size: this.pageSize
        });

      request$.subscribe({
        next: res => {
          this.reports.set(res.content);
          this.total.set(res.totalElements ?? res.page?.totalElements ?? 0);
          this.totalPages.set(res.totalPages ?? res.page?.totalPages ?? 0);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
    }
  }

  // load(): void {
  //   this.loading.set(true);
  //   this.reportService.getMyReports(this.page(), this.pageSize).subscribe({
  //     next: res => {
  //       const content = this.filter() === 'all'
  //         ? res.content
  //         : res.content.filter(r => r.currentStatus === this.filter());
  //       this.reports.set(content);
  //       this.total.set(res.totalElements ?? res.page?.totalElements ?? 0);
  //       this.totalPages.set(res.totalPages ?? res.page?.totalPages ?? 0);
  //       this.loading.set(false);
  //     },
  //     error: () => this.loading.set(false),
  //   });
  // }

  goToPage(p: number): void { this.page.set(p); this.load(); }
  prevPage(): void { if (this.page() > 0) { this.page.update(p => p - 1); this.load(); } }
  nextPage(): void { if (this.page() < this.totalPages() - 1) { this.page.update(p => p + 1); this.load(); } }

  badge(s: string): string { return STATUS_BADGE_CLASS[s as keyof typeof STATUS_BADGE_CLASS] ?? 'badge'; }
  label(s: string): string { return STATUS_LABELS[s as keyof typeof STATUS_LABELS] ?? s; }
  formatDate(d: string): string {
    return new Date(d).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
  }
}
