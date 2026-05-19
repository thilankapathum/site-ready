export type UserRole = 'VENDOR' | 'ENGINEER' | 'ADMIN';

export type ReportStatus =
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'CONDITIONALLY_APPROVED'
  | 'REJECTED'
  | 'RESUBMISSION_REQUIRED';

export type Responsibility = 'VENDOR' | 'ENGINEER' | 'CLOSED';

export interface AuthResponse {
  token: string | null;
  userId: string;
  email: string;
  fullName: string;
  role: UserRole;
  active: boolean;
  message?: string | null;
  /** Admin endpoint repurposes 'message' field for company */
  company?: string;
}

export interface ReportResponse {
  id: string;
  siteId: string;
  project: string;
  namingKey: string;
  currentVersion: number;
  currentStatus: ReportStatus;
  currentResponsibility: Responsibility;
  assignedEngineerName: string | null;
  assignedEngineerId: string | null;
  vendorName: string;
  vendorCompany: string;
  createdAt: string;
  updatedAt: string;
  // Latest version
  latestVersionId: string | null;
  sha256Hash: string | null;
  padesSignatureId: string | null;
  uploadedAt: string | null;
  reviewStatus: ReportStatus | null;
  reviewerNotes: string | null;
  conditions: string | null;
  reviewerName: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface EngineerOption {
  userId: string;
  fullName: string;
  email: string;
  company: string;
}

export const STATUS_LABELS: Record<ReportStatus, string> = {
  PENDING_REVIEW:          'Pending Review',
  APPROVED:                'Approved',
  CONDITIONALLY_APPROVED:  'Conditionally Approved',
  REJECTED:                'Rejected',
  RESUBMISSION_REQUIRED:   'Resubmission Required',
};

export const STATUS_BADGE_CLASS: Record<ReportStatus, string> = {
  PENDING_REVIEW:          'badge badge-info badge-outline',
  APPROVED:                'badge badge-success',
  CONDITIONALLY_APPROVED:  'badge badge-success badge-outline',
  REJECTED:                'badge badge-error',
  RESUBMISSION_REQUIRED:   'badge badge-warning badge-outline',
};
