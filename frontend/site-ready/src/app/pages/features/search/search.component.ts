import {Component, OnInit, signal} from '@angular/core';
import {
  AuthResponse,
  ReportResponse,
  ReportStatus,
  STATUS_BADGE_CLASS,
  STATUS_LABELS
} from '../../../models/api.models';
import {ReportService} from '../../../services/report.service';
import {FormsModule} from '@angular/forms';
import {Router} from '@angular/router';

@Component({
  selector: 'app-search',
  imports: [
    FormsModule
  ],
  templateUrl: './search.component.html',
  styleUrl: './search.component.css'
})
export class SearchComponent implements OnInit {
  reports    = signal<ReportResponse[]>([]);
  loading    = signal(false);
  total      = signal(0);
  page       = signal(0);
  totalPages = signal(0);
  engineers  = signal<AuthResponse[]>([]);

  filters: { siteId: string; project: string; status: string; engineerId: string } = {
    siteId: '', project: '', status: '', engineerId: ''
  };

  constructor(private reportService: ReportService, private router:Router) {}

  ngOnInit(): void {
    this.reportService.getActiveEngineers().subscribe(e => this.engineers.set(e));
    this.search();
  }

  search(): void {
    this.page.set(0);
    this.doSearch();
  }

  doSearch(): void {
    this.loading.set(true);
    this.reportService.search({
      siteId: this.filters.siteId || undefined,
      project: this.filters.project || undefined,
      status: (this.filters.status || undefined) as ReportStatus | undefined,
      engineerId: this.filters.engineerId || undefined,
      page: this.page(),
      size: 25,
    }).subscribe({
      next: page => {
        this.reports.set(page.content);
        this.total.set(page.totalElements);
        this.totalPages.set(page.totalPages);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  reset(): void {
    this.filters = { siteId: '', project: '', status: '', engineerId: '' };
    this.search();
  }

  prevPage(): void { if (this.page() > 0) { this.page.update(p => p - 1); this.doSearch(); } }
  nextPage(): void { if (this.page() < this.totalPages() - 1) { this.page.update(p => p + 1); this.doSearch(); } }

  download(r: ReportResponse): void {
    this.reportService.downloadPdf(r.id, r.currentVersion).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${r.namingKey}_V${r.currentVersion}_stamped.pdf`;
      a.click(); URL.revokeObjectURL(url);
    });
  }

  exportExcel(): void {
    this.reportService.exportExcel({
      ...(this.filters.siteId && { siteId: this.filters.siteId }),
      ...(this.filters.project && { project: this.filters.project }),
      ...(this.filters.status && { status: this.filters.status }),
      ...(this.filters.engineerId && { engineerId: this.filters.engineerId }),
    });
  }

  exportCsv(): void {
    this.reportService.exportCsv({
      ...(this.filters.siteId && { siteId: this.filters.siteId }),
      ...(this.filters.project && { project: this.filters.project }),
      ...(this.filters.status && { status: this.filters.status }),
      ...(this.filters.engineerId && { engineerId: this.filters.engineerId }),
    });
  }

  badge(s: string): string { return STATUS_BADGE_CLASS[s as keyof typeof STATUS_BADGE_CLASS] ?? 'badge'; }
  label(s: string): string { return STATUS_LABELS[s as keyof typeof STATUS_LABELS] ?? s; }
  formatDate(d: string): string { return new Date(d).toLocaleDateString('en-GB', { day:'2-digit', month:'short', year:'numeric' }); }

  openDetail(r: ReportResponse): void {
    this.router.navigate(['/reports', r.id]);
  }
}
