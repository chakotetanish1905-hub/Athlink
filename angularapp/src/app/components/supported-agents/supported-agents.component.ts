import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { TICKET_STATUS } from '../../constants/constant';
import { SupportAgent } from '../../models/support-agent.model';
import { Ticket } from '../../models/ticket.model';
import { AuthService } from '../../services/auth.service';
import { TicketService } from '../../services/ticket.service';

/** Client: agents that worked on the client's tickets, and the tickets each one handled. */
@Component({
  selector: 'app-supported-agents',
  templateUrl: './supported-agents.component.html',
  styleUrls: ['./supported-agents.component.css']
})
export class SupportedAgentsComponent implements OnInit {

  agents: SupportAgent[] = [];
  loading = true;

  selectedAgent: SupportAgent | null = null;
  agentTickets: Ticket[] = [];
  ticketsLoading = false;

  constructor(
    private readonly ticketService: TicketService,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    const userId = this.authService.getUserId();
    if (userId === null) {
      return;
    }
    this.ticketService.getTicketsByUserId(userId).subscribe({
      next: tickets => {
        const unique = new Map<number, SupportAgent>();
        tickets.forEach(t => {
          if (t.supportAgent?.agentId && !unique.has(t.supportAgent.agentId)) {
            unique.set(t.supportAgent.agentId, t.supportAgent);
          }
        });
        this.agents = Array.from(unique.values());
        this.loading = false;
      },
      error: () => (this.loading = false)
    });
  }

  showTicketsWorked(agent: SupportAgent): void {
    if (!agent.agentId) {
      return;
    }
    this.selectedAgent = agent;
    this.agentTickets = [];
    this.ticketsLoading = true;
    this.ticketService.getTicketsByAgentId(agent.agentId).subscribe({
      next: tickets => {
        this.agentTickets = tickets;
        this.ticketsLoading = false;
      },
      error: () => (this.ticketsLoading = false)
    });
  }

  canReview(ticket: Ticket): boolean {
    return ticket.status === TICKET_STATUS.RESOLVED || ticket.status === TICKET_STATUS.CLOSED;
  }

  writeReview(ticket: Ticket): void {
    this.router.navigate(['/client/feedback/add', ticket.ticketId]);
  }

  closeModal(): void {
    this.selectedAgent = null;
  }
}
