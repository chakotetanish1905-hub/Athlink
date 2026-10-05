import { Component, OnInit } from '@angular/core';
import { SupportAgent } from '../../models/support-agent.model';
import { Ticket } from '../../models/ticket.model';
import { AuthService } from '../../services/auth.service';
import { FeedbackService } from '../../services/feedback.service';
import { SupportAgentService } from '../../services/support-agent.service';
import { TicketService } from '../../services/ticket.service';
import { UI } from '../ui-helpers';

// The agents who worked on the logged-in client's tickets.
@Component({
  selector: 'app-supported-agents',
  templateUrl: './supported-agents.component.html',
  styleUrls: ['./supported-agents.component.css']
})
export class SupportedAgentsComponent implements OnInit {

  ui = UI;
  myTickets: Ticket[] = [];
  agents: SupportAgent[] = [];
  reviewedTicketIds: number[] = [];
  loading = true;
  errorMessage = '';
  nameSearch = '';

  // "Tickets Worked" dialog
  workedAgent: SupportAgent | null = null;
  workedTickets: Ticket[] = [];
  workedLoading = false;

  // Agent profile dialog
  profileAgent: SupportAgent | null = null;

  constructor(private ticketService: TicketService, private agentService: SupportAgentService,
              private feedbackService: FeedbackService, private authService: AuthService) {}

  ngOnInit(): void {
    this.loadAgents();
  }

  loadAgents(): void {
    this.loading = true;
    this.errorMessage = '';
    const userId = this.authService.getUserId();
    this.ticketService.getTicketsByUserId(userId).subscribe({
      next: (tickets) => {
        this.myTickets = tickets;
        // Collect each assigned agent once
        this.agents = [];
        for (const t of tickets) {
          const agent = t.supportAgent;
          if (agent && !this.agents.some(a => a.agentId === agent.agentId)) {
            this.agents.push(agent);
          }
        }
        this.loading = false;
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = UI.errorMessage(error, 'Could not load your agents.');
      }
    });
    this.feedbackService.getAllFeedbacksByUserId(userId).subscribe({
      next: (feedbacks) => this.reviewedTicketIds = feedbacks.map(f => f.ticket?.ticketId || 0),
      error: () => this.reviewedTicketIds = []
    });
  }

  get filteredAgents(): SupportAgent[] {
    const text = this.nameSearch.trim().toLowerCase();
    return this.agents.filter(a => !text || a.name.toLowerCase().includes(text) || a.expertise.toLowerCase().includes(text));
  }

  ticketCount(agent: SupportAgent): string {
    const count = this.myTickets.filter(t => t.supportAgent?.agentId === agent.agentId).length;
    return count + (count === 1 ? ' ticket' : ' tickets');
  }

  // GET /api/ticket/agent/{agentId} - the backend only returns this client's tickets
  openWorked(agent: SupportAgent): void {
    this.workedAgent = agent;
    this.workedTickets = [];
    this.workedLoading = true;
    this.ticketService.getTicketsByAgentId(agent.agentId!).subscribe({
      next: (tickets) => {
        this.workedTickets = tickets.sort(UI.newestFirst);
        this.workedLoading = false;
      },
      error: () => this.workedLoading = false
    });
  }

  // GET /api/supportAgent/{agentId} for the latest details
  openProfile(agent: SupportAgent): void {
    this.profileAgent = agent;
    this.agentService.getAgentById(agent.agentId!).subscribe({
      next: (fresh) => this.profileAgent = fresh,
      error: () => this.profileAgent = agent
    });
  }

  // SRS: "Write a Review" only when the ticket is Resolved or Closed
  canReview(t: Ticket): boolean {
    return UI.isDone(t) && !this.reviewedTicketIds.includes(t.ticketId || 0);
  }
}
