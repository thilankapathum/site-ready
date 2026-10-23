import {Component, OnInit, signal} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {DatePipe} from '@angular/common';
import {ReportService} from '../../../services/report.service';
import {STATUS_BADGE_CLASS, STATUS_LABELS, VerificationResponse} from '../../../models/api.models';
import {ACCEPTED_REPORT_FILE_ACCEPT} from '../../../models/file-types';

@Component({
  selector: 'app-verify',
  imports: [DatePipe],
  templateUrl: './verify.component.html',
})
export class VerifyComponent implements OnInit {
  readonly statusLabels = STATUS_LABELS;
  readonly statusBadge = STATUS_BADGE_CLASS;
  readonly accept = ACCEPTED_REPORT_FILE_ACCEPT;

  record = signal<VerificationResponse | null>(null);
  result = signal<VerificationResponse | null>(null);
  notFound = signal(false);
  checking = signal(false);
  error = signal('');

  private versionId = '';

  constructor(private route: ActivatedRoute, private reports: ReportService) {}

  ngOnInit(): void {
    this.versionId = this.route.snapshot.paramMap.get('id') ?? '';
    this.reports.verify(this.versionId).subscribe({
      next: r => this.record.set(r),
      error: () => this.notFound.set(true),
    });
  }

  check(event: Event): void {
    const file = (event.target as HTMLInputElement).files?.[0];
    if (!file) return;
    this.error.set('');
    this.result.set(null);
    this.checking.set(true);
    this.reports.verifyFile(this.versionId, file).subscribe({
      next: r => {
        this.result.set(r);
        this.checking.set(false);
      },
      error: err => {
        this.error.set(err.error?.detail ?? 'Verification failed. Please try again.');
        this.checking.set(false);
      },
    });
  }
}
