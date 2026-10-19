import {Component, computed, OnInit, signal} from '@angular/core';
import {
  AuthResponse,
  ReportResponse,
  ReportStatus,
  STATUS_BADGE_CLASS,
  STATUS_LABELS
} from '../../../models/api.models';
import {labelOf} from '../../../models/file-types';
import {ReportService} from '../../../services/report.service';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {AuthService} from '../../../services/auth/auth.service';

@Component({
  selector: 'app-search',
  imports: [
    FormsModule
  ],
  templateUrl: './search.component.html',
  styleUrl: './search.component.css'
})
export class SearchComponent implements OnInit {
  reports = signal<ReportResponse[]>([]);
  loading = signal(false);
  total = signal(0);
  page = signal(0);
  totalPages = signal(0);
  pageSize = 10;
  engineers = signal<AuthResponse[]>([]);
  user = this.auth.currentUser;

  exportingExcel: boolean = false;
  exportingCsv: boolean = false;

  pageNumbers = computed<number[]>(() => {
    const total = this.totalPages();
    const current = this.page();
    if (total <= 7) return Array.from({length: total}, (_, i) => i);
    const pages: number[] = [];
    // Always show first
    pages.push(0);
    if (current > 2) pages.push(-1); // ellipsis
    for (let i = Math.max(1, current - 1); i <= Math.min(total - 2, current + 1); i++) {
      pages.push(i);
    }
    if (current < total - 3) pages.push(-1); // ellipsis
    // Always show last
    pages.push(total - 1);
    return pages;
  });

  showingText = computed(() => {
    const start = this.page() * this.pageSize + 1;
    const end = Math.min((this.page() + 1) * this.pageSize, this.total());
    return `${start}–${end}`;
  });

  filters: { siteId: string; project: string; rat: string; status: string; engineerId: string } = {
    siteId: '', project: '', status: '', rat: '', engineerId: ''
  };

  constructor(private reportService: ReportService, private router: Router, private route: ActivatedRoute,
              private auth: AuthService) {
  }

  ngOnInit(): void {
    this.reportService.getActiveEngineers().subscribe(e => this.engineers.set(e));
    // URL query params are the source of truth, so filters survive navigating away and back
    this.route.queryParamMap.subscribe(q => {
      this.filters = {
        siteId: q.get('siteId') ?? '',
        project: q.get('project') ?? '',
        rat: q.get('rat') ?? '',
        status: q.get('status') ?? '',
        engineerId: q.get('engineerId') ?? '',
      };
      this.page.set(Number(q.get('page')) || 0);
      this.pageSize = Number(q.get('size')) || 10;
      this.doSearch();
    });
  }

  search(): void {
    this.updateUrl(0);
  }

  private updateUrl(page: number): void {
    const url = this.router.createUrlTree([], {
      relativeTo: this.route,
      queryParams: {
        ...this.buildExportFilters(),
        ...(page > 0 && {page}),
        ...(this.pageSize !== 10 && {size: this.pageSize}),
      },
    });
    // Router ignores same-URL navigation, so re-run the search directly (e.g. Search clicked twice)
    if (this.router.serializeUrl(url) === this.router.url) {
      this.doSearch();
    } else {
      this.router.navigateByUrl(url, {replaceUrl: true});
    }
  }

  doSearch(): void {
    this.loading.set(true);
    this.reportService.search({
      siteId: this.filters.siteId || undefined,
      project: this.filters.project || undefined,
      rat: this.filters.rat || undefined,
      status: (this.filters.status || undefined) as ReportStatus | undefined,
      engineerId: this.filters.engineerId || undefined,
      page: this.page(),
      size: this.pageSize
    }).subscribe({
      next: res => {
        this.reports.set(res.content);
        this.total.set(res.totalElements ?? res.page?.totalElements ?? 0);
        this.totalPages.set(res.totalPages ?? res.page?.totalPages ?? 0);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  reset(): void {
    this.filters = {siteId: '', project: '', rat: '', status: '', engineerId: ''};
    this.search();
  }

  goToPage(p: number): void {
    this.updateUrl(p);
  }

  prevPage(): void {
    if (this.page() > 0) {
      this.updateUrl(this.page() - 1);
    }
  }

  nextPage(): void {
    if (this.page() < this.totalPages() - 1) {
      this.updateUrl(this.page() + 1);
    }
  }

  onPageSizeChange(): void {
    this.updateUrl(0);
  }

  download(r: ReportResponse): void {
    this.reportService.downloadPdf(r.id, r.currentVersion).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${r.namingKey}_V${r.currentVersion}_stamped.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  exportExcel(): void {
    const filters = this.buildExportFilters();
    this.exportingExcel = true;
    this.reportService.exportExcel(filters).subscribe(blob => {
      this.triggerDownload(blob, 'ssv-reports.xlsx',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
      this.exportingExcel = false;
    });
  }

  exportCsv(): void {
    const filters = this.buildExportFilters();
    this.reportService.exportCsv(filters).subscribe(blob => {
      this.exportingCsv = true
      this.triggerDownload(blob, 'ssv-reports.csv', 'text/csv');
      this.exportingCsv = false;
    });
  }

  private buildExportFilters(): Record<string, string> {
    return {
      ...(this.filters.siteId && {siteId: this.filters.siteId}),
      ...(this.filters.project && {project: this.filters.project}),
      ...(this.filters.rat && {rat: this.filters.rat}),
      ...(this.filters.status && {status: this.filters.status}),
      ...(this.filters.engineerId && {engineerId: this.filters.engineerId}),
    };
  }

  private triggerDownload(blob: Blob, filename: string, mimeType: string): void {
    const url = URL.createObjectURL(new Blob([blob], {type: mimeType}));
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }

  badge(s: string): string {
    return STATUS_BADGE_CLASS[s as keyof typeof STATUS_BADGE_CLASS] ?? 'badge';
  }

  label(s: string): string {
    return STATUS_LABELS[s as keyof typeof STATUS_LABELS] ?? s;
  }

  formatDate(d: string): string {
    return new Date(d).toLocaleDateString('en-GB', {day: '2-digit', month: 'short', year: 'numeric'});
  }

  openDetail(r: ReportResponse): void {
    this.router.navigate(['/reports', r.id]);
  }

  isCurrentUser(userId: string) {
    const u = this.user();
    return u !== null && userId === u.userId;
  }

  fileLabel(contentType: string | null | undefined): string {
    return labelOf(contentType);
  }
}
