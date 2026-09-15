import {Component, computed, input, OnInit, signal} from '@angular/core';
import {
  AuthResponse, PageDiff,
  ReportResponse,
  STATUS_BADGE_CLASS,
  STATUS_LABELS,
  VersionResponse
} from '../../../models/api.models';
import {Router} from '@angular/router';
import {extensionOf, isAcceptedReportFile, labelOf} from '../../../models/file-types';
import {ReportService} from '../../../services/report.service';
import {AuthService} from '../../../services/auth/auth.service';
import {AdminService} from '../../../services/admin.service';
import {FormsModule} from '@angular/forms';

type Decision = 'APPROVED' | 'CONDITIONALLY_APPROVED' | 'REJECTED' | 'RESUBMISSION_REQUIRED';

interface HistoryRow {
  rowKey: string;
  versionNumber: number;
  actionLabel: string;
  actionBadgeClass: string;
  actorName: string;
  actorRole: string;
  dateTime: string;
  filename: string;
  notes: string | null;
  conditions: string | null;
  hasFile: boolean;
  isApproved: boolean;
  isRejected: boolean;
  isResubmission: boolean;
  versionId: string;
  downloadType: 'vendor' | 'reviewed';
  contentType: string;
  diff: PageDiff | null;
  hasDeletions: boolean;
  label: string;
}


@Component({
  selector: 'app-report-detail',
  imports: [
    FormsModule
  ],
  templateUrl: './report-detail.component.html',
  styleUrl: './report-detail.component.css'
})
export class ReportDetailComponent implements OnInit {
  readonly id = input.required<string>();

  report = signal<ReportResponse | null>(null);
  versions = signal<VersionResponse[]>([]);

  historyRows = computed<HistoryRow[]>(() => {
    const rows: HistoryRow[] = [];
    const sorted = [...this.versions()].sort((a, b) => a.versionNumber - b.versionNumber);

    for (const v of sorted) {
      // Vendor upload row
      rows.push({
        rowKey:        `${v.id}-upload`,
        versionNumber: v.versionNumber,
        actionLabel:   'Uploaded',
        actionBadgeClass: 'badge badge-info badge-outline',
        actorName:     v.uploaderName,
        actorRole:     v.uploaderRole,
        dateTime:      v.uploadedAt ? this.formatDate(v.uploadedAt) : '—',
        filename:      v.originalFilename,
        notes:         null,
        conditions:    null,
        hasFile:       true,
        isApproved:    false,
        isRejected:    false,
        isResubmission: false,
        versionId:     v.id,
        downloadType:  'vendor',
        contentType:   v.contentType,
        diff:          v.pageDiff,                         // ← vendor's own diff
        hasDeletions:  v.pageDiff?.hasDeletions ?? false,
        label:         'Changes',
      });

      // Engineer review row
      if (v.reviewStatus && v.reviewerName) {
        const isApproved     = v.reviewStatus === 'APPROVED' || v.reviewStatus === 'CONDITIONALLY_APPROVED';
        const isRejected     = v.reviewStatus === 'REJECTED';
        const isResubmission = v.reviewStatus === 'RESUBMISSION_REQUIRED';

        rows.push({
          rowKey:        `${v.id}-review`,
          versionNumber: v.versionNumber,
          actionLabel:   STATUS_LABELS[v.reviewStatus as keyof typeof STATUS_LABELS] ?? v.reviewStatus,
          actionBadgeClass: isApproved     ? 'badge badge-success' :
            isRejected     ? 'badge badge-error' :
              isResubmission ? 'badge badge-warning badge-outline' : 'badge badge-ghost',
          actorName:     v.reviewerName,
          actorRole:     'ENGINEER',
          dateTime:      v.reviewedAt ? this.formatDate(v.reviewedAt) : '—',
          filename:      `Reviewed V${v.versionNumber}`,
          notes:         v.reviewerNotes,
          conditions:    v.conditions,
          hasFile:       v.hasReviewedPdf,
          isApproved,
          isRejected,
          isResubmission,
          versionId:     v.id,
          downloadType:  'reviewed',
          contentType:   v.contentType,
          diff:          v.reviewerPageDiff,               // ← engineer's own diff
          hasDeletions:  v.reviewerPageDiff?.hasDeletions ?? false,
          label:         'Changes',
        });
      }
    }
    return rows;
  });

  canAssignEngineer = computed(() => {
    const r = this.report();
    if (!r) return false;
    // Admin can always reassign
    if (this.isAdmin()) return true;
    // Owning vendor can assign when unassigned or report is still pending
    if (this.isOwnerVendor()) {
      return (!r.assignedEngineerId || r.currentStatus === 'PENDING_REVIEW') && r.currentVersion ===1;
    }
    return false;
  });

  loading = signal(true);
  versionsLoading = signal(true);
  engineers = signal<AuthResponse[]>([]);
  showAssign = signal(false);
  selectedEngineerId = '';
  assigningEngineer = signal(false);

  accessDenied = signal(false);
  notFound = signal(false);

  // Action state
  actionFile = signal<File | null>(null);
  dragging = signal(false);
  decision = signal<Decision | ''>('');
  decisionValue: Decision | '' = '';
  notes = '';
  conditions = '';
  submitting = signal(false);
  actionError = signal('');
  actionSuccess = signal('');

  decisionOptions: { value: Decision; label: string; desc: string }[] = [
    {value: 'APPROVED', label: 'Approved', desc: 'Report meets all requirements'},
    {value: 'CONDITIONALLY_APPROVED', label: 'Conditionally Approved', desc: 'Approved with stated conditions'},
    {value: 'RESUBMISSION_REQUIRED', label: 'Resubmission Required', desc: 'Corrections needed, resubmit'},
    {value: 'REJECTED', label: 'Rejected', desc: 'Cannot be approved'},
  ];

  user = this.auth.currentUser;
  role = this.auth.role;
  isAdmin = computed(() => this.role() === 'ADMIN');
  isVendor = computed(() => this.role() === 'VENDOR');
  isEngineer = computed(() => this.role() === 'ENGINEER');
  isManager = computed(() => this.role() === 'MANAGER');

  // isOwnerVendor = computed(() => {
  //   const r = this.report();
  //   const u = this.user();
  //   return u !== null && r !== null && this.isVendor() &&
  //     r.vendorName === u.fullName; // compare by id in production if available
  // });

  isOwnerVendor = computed(() => {
    const r = this.report();
    const u = this.user();
    if (!u || !r) return false;
    return this.isVendor() && r.vendorId === u.userId;
  });

  isAssignedEngineer = computed(() => {
    const r = this.report();
    const u = this.user();
    return u !== null && r !== null && this.isEngineer() &&
      r.assignedEngineerId === u.userId;
  });

  canTakeAction = computed(() => {
    const r = this.report();
    if (!r || this.actionSuccess()) return false;
    if (this.isOwnerVendor() && r.currentStatus === 'RESUBMISSION_REQUIRED') return true;
    if ((this.isAssignedEngineer() || this.isAdmin()) && r.currentStatus === 'PENDING_REVIEW') return true;
    return false;
  });

  deleting = signal(false);

  constructor(
    public router: Router,
    private reportService: ReportService,
    private auth: AuthService,
    private adminService: AdminService
  ) {
  }

  ngOnInit(): void {
    this.load();
    this.reportService.getActiveEngineers().subscribe(e => this.engineers.set(e));
  }

  load(): void {
    this.loading.set(true);
    this.versionsLoading.set(true);

    this.reportService.getReport(this.id()).subscribe({
      next: r => {
        this.report.set(r);
        this.loading.set(false);
      },
      error: err => {
        this.loading.set(false);
        if (err.status === 403) {
          this.accessDenied.set(true);
        } else {
          this.notFound.set(true);
        }
      }
    });
    this.reportService.getVersions(this.id()).subscribe({
      next: v => {
        this.versions.set(v);
        this.versionsLoading.set(false);
      },
      error: () => this.versionsLoading.set(false),
    });
  }

  assignEngineer(): void {
    this.assigningEngineer.set(true);
    const engId = this.selectedEngineerId || null;
    this.reportService.assignEngineer(this.id(), engId).subscribe({
      next: r => {
        this.report.set(r);
        this.showAssign.set(false);
        this.selectedEngineerId = '';
        this.assigningEngineer.set(false);
        // Show brief success feedback
        this.actionSuccess.set(
          engId ? 'Engineer assigned successfully.' : 'Engineer unassigned.'
        );
        setTimeout(() => this.actionSuccess.set(''), 3000);
      },
      error: err => {
        this.assigningEngineer.set(false);
        this.actionError.set(err.error?.detail ?? 'Failed to assign engineer.');
      },
    });
  }

  downloadLatest(): void {
    const r = this.report();
    if (!r) return;
    this.reportService.downloadPdf(r.id, r.currentVersion).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${r.namingKey}_V${r.currentVersion}_stamped${extensionOf(r.contentType)}`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  downloadVersion(v: VersionResponse): void {
    const r = this.report();
    if (!r) return;
    this.reportService.downloadVersionPdf(r.id, v.id).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${r.namingKey}_V${v.versionNumber}_stamped${extensionOf(v.contentType)}`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  deleteReport(): void {
    const r = this.report();
    if (!r || this.deleting()) return;
    const confirmed = confirm(
      `Delete report ${r.siteId} / ${r.project}? This cannot be undone from here, and the ` +
      `Site ID + Project slot will become available for a new upload.`
    );
    if (!confirmed) return;

    this.deleting.set(true);
    this.adminService.deleteReport(r.id).subscribe({
      next: () => this.router.navigate(['/search']),
      error: err => {
        this.deleting.set(false);
        this.actionError.set(err.error?.detail ?? 'Failed to delete report.');
      },
    });
  }

  onFileSelect(event: Event): void {
    const f = (event.target as HTMLInputElement).files?.[0];
    if (f) this.actionFile.set(f);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const f = event.dataTransfer?.files[0];
    if (f && isAcceptedReportFile(f)) this.actionFile.set(f);
  }

  submitAction(): void {
    const r = this.report();
    const file = this.actionFile();
    if (!r || !file) return;

    if (this.isAssignedEngineer() || this.isAdmin()) {
      // Engineer review
      const d = this.decision();
      if (!d) return;
      if (d === 'CONDITIONALLY_APPROVED' && !this.conditions.trim()) {
        this.actionError.set('Conditions are required for conditional approval.');
        return;
      }
      this.submitting.set(true);
      this.actionError.set('');
      const fd = new FormData();
      fd.append('file', file);
      fd.append('decision', d);
      if (this.notes) fd.append('notes', this.notes);
      if (this.conditions) fd.append('conditions', this.conditions);
      this.reportService.reviewReport(r.id, fd).subscribe({
        next: res => {
          this.report.set(res);
          this.actionSuccess.set('Review submitted successfully.');
          this.actionFile.set(null);
          this.submitting.set(false);
          this.load(); // refresh versions
        },
        error: err => {
          this.actionError.set(err.error?.detail ?? 'Failed to submit review.');
          this.submitting.set(false);
        },
      });
    } else if (this.isOwnerVendor()) {
      // Vendor resubmission
      this.submitting.set(true);
      this.actionError.set('');
      const fd = new FormData();
      fd.append('file', file);
      fd.append('siteId', r.siteId);
      fd.append('project', r.project);
      fd.append('rat', r.rat);
      if (r.assignedEngineerId) fd.append('assignedEngineerId', r.assignedEngineerId);
      this.reportService.uploadReport(fd).subscribe({
        next: res => {
          this.report.set(res);
          this.actionSuccess.set(`V${res.currentVersion} submitted successfully.`);
          this.actionFile.set(null);
          this.submitting.set(false);
          this.load();
        },
        error: err => {
          this.actionError.set(err.error?.detail ?? 'Upload failed.');
          this.submitting.set(false);
        },
      });
    }
  }

  statusBadge(s: string): string {
    return STATUS_BADGE_CLASS[s as keyof typeof STATUS_BADGE_CLASS] ?? 'badge';
  }

  statusLabel(s: string): string {
    return STATUS_LABELS[s as keyof typeof STATUS_LABELS] ?? s;
  }

  formatDate(d: string): string {
    return new Date(d).toLocaleDateString('en-GB', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  formatShortDate(d: string): string {
    return new Date(d).toLocaleDateString('en-GB', {
      day: '2-digit',
      month: 'numeric',
      year: 'numeric'
    });
  }

  formatShortTime(d: string): string {
    return new Date(d).toLocaleTimeString('en-GB', {
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  formatSize(bytes: number): string {
    return bytes > 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${(bytes / 1024).toFixed(0)} KB`;
  }

  fileLabel(contentType: string | null | undefined): string {
    return labelOf(contentType);
  }

  toggleAssign(): void {
    this.showAssign.set(!this.showAssign());
  }

  downloadRow(row: HistoryRow): void {
    const r = this.report();
    if (!r) return;

    const obs = row.downloadType === 'reviewed'
      ? this.reportService.downloadReviewedPdf(r.id, row.versionId)
      : this.reportService.downloadVersionPdf(r.id, row.versionId);

    obs.subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${r.namingKey}_V${row.versionNumber}_${row.downloadType}${extensionOf(row.contentType)}`;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  objectKeys(obj: Record<string, number> | null | undefined): string[] {
    if (!obj) return [];
    return Object.keys(obj);
  }

  isCurrentUser(userId:string):boolean{
    const u = this.user();
    return u !== null && userId === u.userId;
  }

}
