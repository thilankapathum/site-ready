export const ACCEPTED_REPORT_MIME_TYPES = [
  'application/pdf',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
];

export const ACCEPTED_REPORT_FILE_ACCEPT = '.pdf,.xlsx';

export function isAcceptedReportFile(file: File): boolean {
  return ACCEPTED_REPORT_MIME_TYPES.includes(file.type);
}

export function extensionOf(contentType: string | null | undefined): string {
  return contentType === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    ? '.xlsx' : '.pdf';
}
