import {Component, input, OnInit, signal} from '@angular/core';
import {ReportResponse, STATUS_BADGE_CLASS, STATUS_LABELS} from '../../../models/api.models';
import {Router} from '@angular/router';
import {ReportService} from '../../../services/report.service';
import {FormsModule} from '@angular/forms';

type Decision = 'APPROVED' | 'CONDITIONALLY_APPROVED' | 'REJECTED' | 'RESUBMISSION_REQUIRED';

@Component({
  selector: 'app-review-detail',
  imports: [
    FormsModule
  ],
  templateUrl: './review-detail.component.html',
  styleUrl: './review-detail.component.css'
})
export class ReviewDetailComponent implements OnInit {
  readonly id = input.required<string>();

  report     = signal<ReportResponse | null>(null);
  loading    = signal(true);
  reviewedFile = signal<File | null>(null);
  dragging   = signal(false);
  decision   = signal<Decision | ''>('');
  decisionValue: Decision | '' = '';
  notes      = '';
  conditions = '';
  submitting = signal(false);
  reviewError  = signal('');
  reviewSuccess = signal('');

  decisionOptions: { value: Decision; label: string; desc: string }[] = [
    { value: 'APPROVED',               label: 'Approved',               desc: 'Report meets all requirements' },
    { value: 'CONDITIONALLY_APPROVED', label: 'Conditionally Approved', desc: 'Approved with stated conditions' },
    { value: 'RESUBMISSION_REQUIRED',  label: 'Resubmission Required',  desc: 'Corrections needed, resubmit' },
    { value: 'REJECTED',               label: 'Rejected',               desc: 'Cannot be approved' },
  ];

  constructor(public router: Router, private reportService: ReportService) {}

  ngOnInit(): void {
    // Load report from my reports (simplified — production should have a GET /reports/:id endpoint)
    this.reportService.search({ page: 0, size: 200 }).subscribe({
      next: page => {
        const found = page.content.find(r => r.id === this.id());
        this.report.set(found ?? null);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  download(): void {
    const r = this.report();
    if (!r) return;
    this.reportService.downloadPdf(r.id, r.currentVersion).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${r.namingKey}_V${r.currentVersion}_stamped.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  onFileSelect(event: Event): void {
    const f = (event.target as HTMLInputElement).files?.[0];
    if (f) this.reviewedFile.set(f);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault(); this.dragging.set(false);
    const f = event.dataTransfer?.files[0];
    if (f?.type === 'application/pdf') this.reviewedFile.set(f);
  }

  submitReview(): void {
    const r = this.report();
    const file = this.reviewedFile();
    const d = this.decision();
    if (!r || !file || !d) return;
    if (d === 'CONDITIONALLY_APPROVED' && !this.conditions.trim()) {
      this.reviewError.set('Conditions are required for conditional approval.'); return;
    }
    this.submitting.set(true); this.reviewError.set('');
    const fd = new FormData();
    fd.append('file', file);
    fd.append('decision', d);
    if (this.notes) fd.append('notes', this.notes);
    if (this.conditions) fd.append('conditions', this.conditions);

    this.reportService.reviewReport(r.id, fd).subscribe({
      next: res => {
        this.report.set(res);
        this.reviewSuccess.set('Review submitted successfully.');
        this.submitting.set(false);
      },
      error: err => {
        this.reviewError.set(err.error?.detail ?? 'Failed to submit review.');
        this.submitting.set(false);
      },
    });
  }

  badge(s: string): string { return STATUS_BADGE_CLASS[s as keyof typeof STATUS_BADGE_CLASS] ?? 'badge'; }
  label(s: string): string { return STATUS_LABELS[s as keyof typeof STATUS_LABELS] ?? s; }
  formatDate(d: string): string { return new Date(d).toLocaleDateString('en-GB', { day:'2-digit', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit' }); }
  formatSize(bytes: number): string { return bytes > 1048576 ? `${(bytes/1048576).toFixed(1)} MB` : `${(bytes/1024).toFixed(0)} KB`; }
}
