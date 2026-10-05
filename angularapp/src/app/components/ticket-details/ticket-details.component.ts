import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Ticket } from '../../models/ticket.model';
import { AuthService } from '../../services/auth.service';
import { FeedbackService } from '../../services/feedback.service';
import { TicketService } from '../../services/ticket.service';
import { UI } from '../ui-helpers';

interface TimelineStep {
  title: string;
  text: string;
  done: boolean;
  success: boolean;
}

// Ticket details for both roles.
// Client: GET /api/ticket/{id}. Manager: the SRS allows GET /api/ticket only, so the ticket is found in that list.
@Component({
  selector: 'app-ticket-details',
  templateUrl: './ticket-details.component.html',
  styleUrls: ['./ticket-details.component.css']
})
export class TicketDetailsComponent implements OnInit {

  ui = UI;
  isManager = false;
  ticketId = 0;
  ticket: Ticket | null = null;
  reviewed = false;
  loading = true;
  notFound = false;
  errorMessage = '';
  actionError = '';

  // Dialogs
  showSummaryForm = false;
  showConfirmSummary = false;
  showSummary = false;
  showDelete = false;
  summaryText = '';
  summarySatisfied = '';    // 'yes' | 'no'
  summaryError = false;

  toastTitle = '';

  constructor(private route: ActivatedRoute, private router: Router, private ticketService: TicketService,
              private feedbackService: FeedbackService, private authService: AuthService) {}

  ngOnInit(): void {
    this.isManager = this.authService.isManager();
    this.ticketId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadTicket();
  }

  loadTicket(): void {
    this.loading = true;
    this.errorMessage = '';
    if (this.isManager) {
      this.ticketService.getAllTickets().subscribe({
        next: (tickets) => {
          this.ticket = tickets.find(t => t.ticketId === this.ticketId) || null;
          this.notFound = this.ticket === null;
          this.loading = false;
        },
        error: (error) => this.showLoadError(error)
      });
    } else {
      this.ticketService.getTicketById(this.ticketId).subscribe({
        next: (ticket) => {
          this.ticket = ticket;
          this.loading = false;
        },
        error: (error) => this.showLoadError(error)
      });
      this.feedbackService.getAllFeedbacksByUserId(this.authService.getUserId()).subscribe({
        next: (feedbacks) => this.reviewed = feedbacks.some(f => f.ticket?.ticketId === this.ticketId),
        error: () => this.reviewed = false
      });
    }
  }

  get backLink(): string {
    return this.isManager ? '/manager/tickets' : '/client/tickets';
  }

  // ---------- which actions are shown ----------

  get isActive(): boolean {
    return !!this.ticket && (this.ticket.status === 'Open' || this.ticket.status === 'In Progress');
  }

  get canAssign(): boolean {
    return this.isManager && this.ticket?.status === 'Open' && !this.ticket?.supportAgent;
  }

  get canClose(): boolean {
    return this.isManager && this.ticket?.status === 'Resolved';
  }

  get canEdit(): boolean {
    return !this.isManager && this.ticket?.status === 'Open' && !this.ticket?.supportAgent;
  }

  get canResolve(): boolean {
    return !this.isManager && this.isActive && !!this.ticket?.supportAgent;
  }

  get canReview(): boolean {
    return !this.isManager && !!this.ticket && UI.isDone(this.ticket) && !!this.ticket.supportAgent && !this.reviewed;
  }

  get timeline(): TimelineStep[] {
    const t = this.ticket;
    if (!t) { return []; }
    const done = UI.isDone(t);
    return [
      { title: 'Ticket created', text: UI.formatDate(t.createdDate) + ' · by ' + (t.user?.username || 'client'), done: true, success: false },
      { title: 'Agent assigned', text: t.supportAgent ? t.supportAgent.name + ' · ' + t.supportAgent.expertise : 'Waiting for a manager', done: !!t.supportAgent, success: false },
      { title: 'Resolution summary added', text: t.resolutionSummary ? 'Satisfaction: ' + UI.satisfaction(t).label : 'Required before resolving', done: !!t.resolutionSummary, success: false },
      { title: 'Resolved', text: t.resolutionDate ? UI.formatDate(t.resolutionDate) : 'Pending', done: done, success: true },
      { title: 'Closed', text: t.status === 'Closed' ? 'Closed by manager' : 'Pending', done: t.status === 'Closed', success: true }
    ];
  }

  // ---------- manager actions ----------

  // The assign dialog lives on the tickets page; open it there for this ticket
  assignAgent(): void {
    this.router.navigate(['/manager/tickets'], { queryParams: { assign: this.ticketId } });
  }

  closeTicket(): void {
    this.save({ ...this.ticket!, status: 'Closed' }, 'Ticket closed');
  }

  // ---------- client actions ----------

  openSummaryForm(): void {
    this.summaryText = this.ticket?.resolutionSummary || '';
    this.summarySatisfied = this.ticket?.satisfied === true ? 'yes' : (this.ticket?.satisfied === false ? 'no' : '');
    this.summaryError = false;
    this.showSummaryForm = true;
  }

  submitSummary(): void {
    if (!this.summaryText.trim() || !this.summarySatisfied) {
      this.summaryError = true;
      return;
    }
    this.showSummaryForm = false;
    this.showConfirmSummary = true;
  }

  confirmSummary(): void {
    this.showConfirmSummary = false;
    const updated: Ticket = { ...this.ticket!, resolutionSummary: this.summaryText.trim(), satisfied: this.summarySatisfied === 'yes' };
    this.save(updated, 'Resolution summary saved');
  }

  markResolved(): void {
    if (!this.ticket?.resolutionSummary) {
      this.actionError = 'Please provide resolution details before marking resolve.';
      return;
    }
    this.save({ ...this.ticket, status: 'Resolved' }, 'Ticket marked as resolved');
  }

  confirmDelete(): void {
    this.ticketService.deleteTicket(this.ticketId).subscribe({
      next: () => this.router.navigate(['/client/tickets']),
      error: (error) => {
        this.showDelete = false;
        this.actionError = UI.errorMessage(error, 'Could not delete the ticket.');
      }
    });
  }

  private save(updated: Ticket, message: string): void {
    this.actionError = '';
    this.ticketService.updateTicket(this.ticketId, updated).subscribe({
      next: (saved) => {
        this.ticket = saved;
        this.toastTitle = message;
        setTimeout(() => this.toastTitle = '', 4200);
      },
      error: (error) => this.actionError = UI.errorMessage(error, 'Could not update the ticket.')
    });
  }

  private showLoadError(error: any): void {
    this.loading = false;
    if (error.status === 404 || error.status === 403) {
      this.notFound = true;
    } else {
      this.errorMessage = UI.errorMessage(error, 'Could not load this ticket.');
    }
  }
}
