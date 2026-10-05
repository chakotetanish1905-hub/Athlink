import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Ticket } from '../../models/ticket.model';
import { AuthService } from '../../services/auth.service';
import { FeedbackService } from '../../services/feedback.service';
import { TicketService } from '../../services/ticket.service';
import { UI } from '../ui-helpers';

@Component({
  selector: 'app-client-view-tickets',
  templateUrl: './client-view-tickets.component.html',
  styleUrls: ['./client-view-tickets.component.css']
})
export class ClientViewTicketsComponent implements OnInit {

  ui = UI;
  tickets: Ticket[] = [];
  reviewedTicketIds: number[] = [];
  loading = true;
  errorMessage = '';

  // Search by title / issue category and filter by priority (SRS), plus status tabs
  searchText = '';
  priorityFilter = '';
  statusFilter = '';
  statusTabs = ['', 'Open', 'In Progress', 'Resolved', 'Closed'];

  deleteTicket: Ticket | null = null;
  deleteError = '';
  toastTitle = '';

  constructor(private ticketService: TicketService, private feedbackService: FeedbackService,
              private authService: AuthService, private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      this.searchText = params['q'] || '';
      this.statusFilter = params['status'] || '';
    });
    this.loadTickets();
  }

  loadTickets(): void {
    this.loading = true;
    this.errorMessage = '';
    const userId = this.authService.getUserId();
    this.ticketService.getTicketsByUserId(userId).subscribe({
      next: (tickets) => {
        this.tickets = tickets.sort(UI.newestFirst);
        this.loading = false;
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = UI.errorMessage(error, 'Could not load your tickets.');
      }
    });
    // Used to show "Review" only for tickets without feedback
    this.feedbackService.getAllFeedbacksByUserId(userId).subscribe({
      next: (feedbacks) => this.reviewedTicketIds = feedbacks.map(f => f.ticket?.ticketId || 0),
      error: () => this.reviewedTicketIds = []
    });
  }

  get filteredTickets(): Ticket[] {
    return this.applyFilters(this.statusFilter);
  }

  countForTab(status: string): number {
    return this.applyFilters(status).length;
  }

  get anyFilter(): boolean {
    return !!(this.searchText || this.priorityFilter || this.statusFilter);
  }

  clearFilters(): void {
    this.searchText = '';
    this.priorityFilter = '';
    this.statusFilter = '';
  }

  // SRS: edit / delete only while the ticket is Open and no agent is assigned
  canEdit(t: Ticket): boolean {
    return t.status === 'Open' && !t.supportAgent;
  }

  canReview(t: Ticket): boolean {
    return UI.isDone(t) && !!t.supportAgent && !this.reviewedTicketIds.includes(t.ticketId || 0);
  }

  askDelete(ticket: Ticket): void {
    this.deleteError = '';
    this.deleteTicket = ticket;
  }

  confirmDelete(): void {
    if (!this.deleteTicket) { return; }
    const ticket = this.deleteTicket;
    this.ticketService.deleteTicket(ticket.ticketId!).subscribe({
      next: () => {
        this.deleteTicket = null;
        this.tickets = this.tickets.filter(t => t.ticketId !== ticket.ticketId);
        this.toastTitle = 'Ticket #' + ticket.ticketId + ' deleted';
        setTimeout(() => this.toastTitle = '', 4200);
      },
      error: (error) => this.deleteError = UI.errorMessage(error, 'Could not delete the ticket.')
    });
  }

  private applyFilters(status: string): Ticket[] {
    const text = this.searchText.trim().toLowerCase().replace('#', '');
    const result: Ticket[] = [];
    for (const t of this.tickets) {
      const matchesText = !text || t.title.toLowerCase().includes(text)
        || t.issueCategory.toLowerCase().includes(text) || String(t.ticketId) === text;
      if (matchesText && (!this.priorityFilter || t.priority === this.priorityFilter) && (!status || t.status === status)) {
        result.push(t);
      }
    }
    return result;
  }
}
