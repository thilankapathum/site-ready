import {Component, OnInit, signal} from '@angular/core';
import {AuthResponse} from '../../../models/api.models';
import {isAcceptedReportFile} from '../../../models/file-types';
import {ReportService} from '../../../services/report.service';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-upload',
  imports: [
    FormsModule
  ],
  templateUrl: './upload.component.html',
  styleUrl: './upload.component.css'
})
export class UploadComponent implements OnInit {
  selectedFile = signal<File | null>(null);
  dragging     = signal(false);
  uploading    = signal(false);
  success      = signal('');
  error        = signal('');
  engineers    = signal<AuthResponse[]>([]);

  siteId = '';
  project = '';
  rat = '';
  parsedRat = signal('');
  assignedEngineerId = '';

  parsedSiteId  = signal('');
  parsedProject = signal('');

  constructor(private reportService: ReportService) {}

  ngOnInit(): void {
    this.reportService.getActiveEngineers().subscribe({
      next: eng => this.engineers.set(eng),
    });
  }

  onFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files?.[0]) this.setFile(input.files[0]);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    const file = event.dataTransfer?.files[0];
    if (file && isAcceptedReportFile(file)) this.setFile(file);
    else this.error.set('Only PDF or Excel (.xlsx) files are accepted.');
  }

  setFile(file: File): void {
    this.selectedFile.set(file);
    this.error.set('');
    this.success.set('');
    this.parseFilename(file.name);
  }

  clearFile(): void {
    this.selectedFile.set(null);
    this.parsedSiteId.set('');
    this.parsedProject.set('');
  }

  parseFilename(name: string): void {
    const match = name.replace(/\.(pdf|xlsx)$/i, '').match(/^([^_]+)_(.+?)_([^_]+)_V\d+$/i);
    if (match) {
      this.parsedSiteId.set(match[1]);
      this.parsedProject.set(match[2]);
      this.parsedRat.set(match[3].toUpperCase());
      if (!this.siteId)  this.siteId  = match[1];
      if (!this.project) this.project = match[2];
      if (!this.rat)     this.rat     = match[3].toUpperCase();
    }
  }

  upload(): void {
    const file = this.selectedFile();
    if (!file || !this.siteId || !this.project) return;
    this.uploading.set(true);
    this.error.set('');
    this.success.set('');

    const fd = new FormData();
    fd.append('file', file);
    fd.append('siteId', this.siteId.trim());
    fd.append('project', this.project.trim());
    fd.append('rat',      this.rat.trim().toUpperCase());
    if (this.assignedEngineerId) fd.append('assignedEngineerId', this.assignedEngineerId);

    this.reportService.uploadReport(fd).subscribe({
      next: res => {
        this.success.set(`Report ${res.namingKey} V${res.currentVersion} uploaded and stamped successfully.`);
        this.clearFile();
        this.siteId = ''; this.project = ''; this.assignedEngineerId = ''; this.rat = '';
        this.uploading.set(false);
      },
      error: err => {
        this.error.set(err.error?.detail ?? 'Upload failed. Please try again.');
        this.uploading.set(false);
      },
    });
  }

  formatSize(bytes: number): string {
    return bytes > 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${(bytes / 1024).toFixed(0)} KB`;
  }
}
