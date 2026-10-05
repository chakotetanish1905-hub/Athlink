import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { AGENT_STATUS, CATEGORY_EXPERTISE_MAP, TICKET_STATUS } from '../../constants/constant';
import { SupportAgent } from '../../models/support-agent.model';
import { Ticket } from '../../models/ticket.model';
import { ErrorHandlerService } from '../../services/error-handler.service';
import { NotificationService } from '../../services/notification.service';
import { SupportAgentService } from '../../services/support-agent.service';
import { TicketService } from '../../services/ticket.service';

/** Manager: "Available Tickets" - assign agents and close resolved tickets. */
@Component({
  selector: 'app-manager-view-tickets',
  templateUrl: './manager-view-tickets.component.html',
  styleUrls: ['./manager-view-tickets.component.css']
})
export class ManagerViewTicketsComponent implements OnInit {

  tickets: Ticket[] = [];
  agents: SupportAgent[] = [];
  searchText = '';
  loading = true;

  assignTicket: Ticket | null = null;
  showManualSelect = false;
  manualSearch = '';

  constructor(
    private readonly ticketService: TicketService,
    private readonly agentService: SupportAgentService,
    private readonly notification: NotificationService,
    private readonly errorHandler: ErrorHandlerService
  ) {}

  ngOnInit(): void {
    this.loadTickets();
    this.agentService.getAllAgents().subscribe({ next: agents => (this.agents = agents), error: () => (this.agents = []) });
  }

  get filteredTickets(): Ticket[] {
    const term = this.searchText.trim().toLowerCase();
    return this.tickets.filter(t =>
      !term || t.title.toLowerCase().includes(term) || t.issueCategory.toLowerCase().includes(term));
  }

  get availableAgents(): SupportAgent[] {
    return this.agents.filter(a => a.status === AGENT_STATUS.AVAILABLE);
  }

  /** Available agents whose expertise matches the ticket's issue category. */
  get suggestedAgents(): SupportAgent[] {
    if (!this.assignTicket) {
      return [];
    }
    const category = this.assignTicket.issueCategory;
    const wanted = (CATEGORY_EXPERTISE_MAP[category] ?? []).map(e => e.toLowerCase());
    const categoryLower = category.toLowerCase();
    return this.availableAgents.filter(a => {
      const expertise = a.expertise.toLowerCase();
      return wanted.includes(expertise) || expertise.includes(categoryLower) || categoryLower.includes(expertise);
    });
  }

  get manualAgents(): SupportAgent[] {
    const term = this.manualSearch.trim().toLowerCase();
    return this.availableAgents.filter(a =>
      !term || a.name.toLowerCase().includes(term) || a.expertise.toLowerCase().includes(term));
  }

  isOpen(ticket: Ticket): boolean {
    return ticket.status === TICKET_STATUS.OPEN;
  }

  canClose(ticket: Ticket): boolean {
    return ticket.status === TICKET_STATUS.RESOLVED;
  }

  openAssign(ticket: Ticket): void {
    this.assignTicket = ticket;
    this.showManualSelect = false;
    this.manualSearch = '';
  }

  closeAssign(): void {
    this.assignTicket = null;
  }

  assign(agent: SupportAgent): void {
    const ticket = this.assignTicket;
    if (!ticket?.ticketId || !agent.agentId) {
      return;
    }
    this.ticketService.updateTicket(ticket.ticketId, { ...ticket, agentId: agent.agentId }).subscribe({
      next: () => {
        this.notification.success(`${agent.name} assigned to "${ticket.title}".`);
        this.closeAssign();
        this.loadTickets();
      },
      error: (err: HttpErrorResponse) => this.notification.error(this.errorHandler.getMessage(err))
    });
  }

  closeTicket(ticket: Ticket): void {
    if (!ticket.ticketId) {
      return;
    }
    this.ticketService.updateTicket(ticket.ticketId, { ...ticket, status: TICKET_STATUS.CLOSED }).subscribe({
      next: () => {
        this.notification.success(`Ticket "${ticket.title}" closed.`);
        this.loadTickets();
      },
      error: (err: HttpErrorResponse) => this.notification.error(this.errorHandler.getMessage(err))
    });
  }

  private loadTickets(): void {
    this.loading = true;
    this.ticketService.getAllTickets().subscribe({
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
