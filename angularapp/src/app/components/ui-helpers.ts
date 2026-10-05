import { Ticket } from '../models/ticket.model';

// Small display helpers shared by the components (values come from the Design system reference).
// A component exposes them to its template with:  ui = UI;

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

// Avatar background / text colour pairs
const AVATAR_COLORS = [
  ['#E0E7FF', '#3730A3'], ['#DCFCE7', '#166534'], ['#FEF3C7', '#92400E'], ['#FCE7F3', '#9D174D'],
  ['#E0F2FE', '#075985'], ['#EDE9FE', '#5B21B6'], ['#F1F5F9', '#334155']
];

function statusClass(status?: string): string {
  if (status === 'In Progress') { return 'st-progress'; }
  if (status === 'Resolved') { return 'st-resolved'; }
  if (status === 'Closed') { return 'st-closed'; }
  return 'st-open';
}

function statusIcon(status?: string): string {
  if (status === 'In Progress') { return 'i-circle-half'; }
  if (status === 'Resolved') { return 'i-circle-check'; }
  if (status === 'Closed') { return 'i-lock'; }
  return 'i-circle';
}

function priorityClass(priority?: string): string {
  if (priority === 'High') { return 'p-high'; }
  if (priority === 'Medium') { return 'p-medium'; }
  return 'p-low';
}

// Initials + a colour picked from the name, so the same person always gets the same colour
function avatar(name?: string | null): { ini: string; bg: string; fg: string } {
  const text = (name || '?').trim() || '?';
  let hash = 0;
  for (let i = 0; i < text.length; i++) {
    hash = (hash * 31 + text.charCodeAt(i)) % 997;
  }
  const color = AVATAR_COLORS[hash % AVATAR_COLORS.length];
  const words = text.split(/\s+/);
  let ini = '';
  for (let i = 0; i < words.length && ini.length < 2; i++) {
    ini += words[i].charAt(0);
  }
  return { ini: ini.toUpperCase(), bg: color[0], fg: color[1] };
}

// "2026-10-05" -> "Oct 5, 2026"
function formatDate(date?: string | null): string {
  if (!date) { return '—'; }
  const parts = date.substring(0, 10).split('-');
  return MONTHS[Number(parts[1]) - 1] + ' ' + Number(parts[2]) + ', ' + parts[0];
}

// "2026-10-05" -> "Oct 5"
function shortDate(date?: string | null): string {
  if (!date) { return '—'; }
  const parts = date.substring(0, 10).split('-');
  return MONTHS[Number(parts[1]) - 1] + ' ' + Number(parts[2]);
}

// "Today", "Yesterday", "3 days ago" or "Oct 5"
function relativeDate(date?: string | null): string {
  if (!date) { return '—'; }
  const days = Math.round((Date.parse(todayIso()) - Date.parse(date.substring(0, 10))) / 86400000);
  if (days <= 0) { return 'Today'; }
  if (days === 1) { return 'Yesterday'; }
  if (days < 7) { return days + ' days ago'; }
  return shortDate(date);
}

// Today's date as "yyyy-mm-dd" (local time)
function todayIso(): string {
  const d = new Date();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return d.getFullYear() + '-' + month + '-' + day;
}

function isDone(ticket: Ticket): boolean {
  return ticket.status === 'Resolved' || ticket.status === 'Closed';
}

// Satisfaction indicator used by the dashboard and the ticket pages
function satisfaction(ticket: Ticket): { label: string; cls: string; icon: string } {
  if (!isDone(ticket)) {
    return { label: 'Pending resolution', cls: 'pending', icon: 'i-clock' };
  }
  if (ticket.satisfied === true) {
    return { label: 'Satisfied', cls: 'yes', icon: 'i-smile' };
  }
  if (ticket.satisfied === false) {
    return { label: 'Not satisfied', cls: 'no', icon: 'i-frown' };
  }
  return { label: 'Awaiting response', cls: 'pending', icon: 'i-clock' };
}

// [1,2,3,4,5] -> 'on' for every filled star
function stars(rating: number): string[] {
  const list: string[] = [];
  for (let i = 1; i <= 5; i++) {
    list.push(i <= rating ? 'on' : '');
  }
  return list;
}

// Newest ticket first
function newestFirst(a: Ticket, b: Ticket): number {
  const dateA = a.createdDate || '';
  const dateB = b.createdDate || '';
  if (dateA !== dateB) { return dateA < dateB ? 1 : -1; }
  return (b.ticketId || 0) - (a.ticketId || 0);
}

// Reads the "message" sent by the backend GlobalExceptionHandler
function errorMessage(error: any, fallback: string): string {
  if (error && error.error && error.error.message) {
    return error.error.message;
  }
  if (error && error.status === 0) {
    return 'Unable to connect to SupportSphere. Check your connection and try again.';
  }
  return fallback;
}

export const UI = {
  statusClass, statusIcon, priorityClass, avatar, formatDate, shortDate, relativeDate, todayIso,
  isDone, satisfaction, stars, newestFirst, errorMessage
};
