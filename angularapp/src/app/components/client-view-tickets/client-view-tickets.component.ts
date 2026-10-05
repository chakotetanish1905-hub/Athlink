import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { PRIORITIES, TICKET_STATUS } from '../../constants/constant';
import { Ticket } from '../../models/ticket.model';
import { AuthService } from '../../services/auth.service';
import { ErrorHandlerService } from '../../services/error-handler.service';
import { NotificationService } from '../../services/notification.service';
import { TicketService } from '../../services/ticket.service';

/** Client: "Your Tickets" with search (title / issue category) and priority filter. */
@Component({
  selector: 'app-client-view-tickets',
  templateUrl: './client-view-tickets.component.html',
  styleUrls: ['./client-view-tickets.component.css']
})
export class ClientViewTicketsComponent implements OnInit {

  readonly priorities = PRIORITIES;
  tickets: Ticket[] = [];
  searchText = '';
  priorityFilter = '';
  loading = true;
  ticketToDelete: Ticket | null = null;

  constructor(
    private readonly ticketService: TicketService,
    private readonly authService: AuthService,
    private readonly notification: NotificationService,
    private readonly errorHandler: ErrorHandlerService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.loadTickets();
  }

  get filteredTickets(): Ticket[] {
    const term = this.searchText.trim().toLowerCase();
    return this.tickets.filter(t =>
      (!term || t.title.toLowerCase().includes(term) || t.issueCategory.toLowerCase().includes(term)) &&
      (!this.priorityFilter || t.priority === this.priorityFilter));
  }

  /** Edit/Delete are only allowed while the ticket is Open and no agent is assigned. */
  canModify(ticket: Ticket): boolean {
    return ticket.status === TICKET_STATUS.OPEN && !ticket.agentId;
  }

  editTicket(ticket: Ticket): void {
    this.router.navigate(['/client/tickets/edit', ticket.ticketId]);
  }

  viewAgent(ticket: Ticket): void {
    this.router.navigate(['/client/tickets', ticket.ticketId]);
  }

  confirmDelete(): void {
    const ticket = this.ticketToDelete;
    this.ticketToDelete = null;
    if (!ticket?.ticketId) {
      return;
    }
    this.ticketService.deleteTicket(ticket.ticketId).subscribe({
      next: () => {
        this.notification.success('Ticket deleted successfully.');
        this.loadTickets();
      },
      error: (err: HttpErrorResponse) => this.notification.error(this.errorHandler.getMessage(err))
    });
  }

  private loadTickets(): void {
    const userId = this.authService.getUserId();
    if (userId === null) {
      return;
    }
    this.loading = true;
    this.ticketService.getTicketsByUserId(userId).subscribe({
      next: tickets => {
        this.tickets = tickets;
        this.loading = false;
      },
      error: () => {
        this.tickets = [];
        this.loading = false;
      }
    });
  }
}
