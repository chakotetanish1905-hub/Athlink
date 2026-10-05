import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { SupportAgent } from '../../models/support-agent.model';
import { Ticket } from '../../models/ticket.model';
import { SupportAgentService } from '../../services/support-agent.service';
import { TicketService } from '../../services/ticket.service';
import { UI } from '../ui-helpers';

// Which agent expertise fits which ticket category (used for "Suggested" agents)
const MATCHING_EXPERTISE: { [category: string]: string[] } = {
  Technical: ['Technical Support', 'Software Support', 'Networking'],
  Billing: ['Billing'],
  General: ['General Support']
};

@Component({
  selector: 'app-manager-view-tickets',
  templateUrl: './manager-view-tickets.component.html',
  styleUrls: ['./manager-view-tickets.component.css']
})
export class ManagerViewTicketsComponent implements OnInit {

  ui = UI;
  tickets: Ticket[] = [];
  agents: SupportAgent[] = [];
  loading = true;
  errorMessage = '';

  // Filters
  searchText = '';
  priorityFilter = '';
  statusFilter = '';
  categoryFilter = '';
  statusTabs = ['', 'Open', 'In Progress', 'Resolved', 'Closed'];

  // Row "more" menu
  menuTicket: Ticket | null = null;
  menuX = 0;
  menuY = 0;

  // Dialogs
  assignTicket: Ticket | null = null;
  assignTab = 'suggested';
  assignSearch = '';
  summaryTicket: Ticket | null = null;
  profileTicket: Ticket | null = null;
  actionError = '';

  toastTitle = '';
  toastText = '';
  toastKind = 'ok';

  constructor(private ticketService: TicketService, private agentService: SupportAgentService,
              private route: ActivatedRoute) {}

  ngOnInit(): void {
    // Filters can come from the dashboard KPIs or the top-bar search
    this.route.queryParams.subscribe(params => {
      this.searchText = params['q'] || '';
      this.statusFilter = params['status'] || '';
    });
    this.loadTickets();
  }

  // "Assign agent" on the ticket detail page links here with ?assign=<ticketId>
  private openAssignFromLink(): void {
    const assignId = Number(this.route.snapshot.queryParamMap.get('assign'));
    const ticket = this.tickets.find(t => t.ticketId === assignId);
    if (ticket && this.canAssign(ticket)) {
      this.openAssign(ticket);
    }
  }

  loadTickets(): void {
    this.loading = true;
    this.errorMessage = '';
    this.ticketService.getAllTickets().subscribe({
      next: (tickets) => {
        this.tickets = tickets.sort(UI.newestFirst);
        this.loading = false;
        this.openAssignFromLink();
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = UI.errorMessage(error, 'Could not load tickets.');
      }
    });
    this.agentService.getAllAgents().subscribe({
      next: (agents) => this.agents = agents,
      error: () => this.agents = []
    });
  }

  // ---------- filtering ----------

  // Search by title or issue category (SRS), plus priority / status / category filters
  get filteredTickets(): Ticket[] {
    return this.applyFilters(this.statusFilter);
  }

  countForTab(status: string): number {
    return this.applyFilters(status).length;
  }

  get anyFilter(): boolean {
    return !!(this.searchText || this.priorityFilter || this.statusFilter || this.categoryFilter);
  }

  clearFilters(): void {
    this.searchText = '';
    this.priorityFilter = '';
    this.statusFilter = '';
    this.categoryFilter = '';
  }

  private applyFilters(status: string): Ticket[] {
    const text = this.searchText.trim().toLowerCase().replace('#', '');
    const result: Ticket[] = [];
    for (const t of this.tickets) {
      const matchesText = !text || t.title.toLowerCase().includes(text)
        || t.issueCategory.toLowerCase().includes(text) || String(t.ticketId) === text;
      if (matchesText && (!this.priorityFilter || t.priority === this.priorityFilter)
          && (!status || t.status === status) && (!this.categoryFilter || t.issueCategory === this.categoryFilter)) {
        result.push(t);
      }
    }
    return result;
  }

  // ---------- row actions ----------

  // SRS: "Assign Agent" while Open with no agent, "Agent Assigned" once assigned, "Close Ticket" once Resolved
  canAssign(t: Ticket): boolean {
    return t.status === 'Open' && !t.supportAgent;
  }

  isAssigned(t: Ticket): boolean {
    return (t.status === 'Open' || t.status === 'In Progress') && !!t.supportAgent;
  }

  canClose(t: Ticket): boolean {
    return t.status === 'Resolved';
  }

  openMenu(event: MouseEvent, ticket: Ticket): void {
    const button = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const menuHeight = 200;
    this.menuX = Math.max(8, Math.min(button.right - 216, window.innerWidth - 224));
    this.menuY = button.bottom + 4 + menuHeight > window.innerHeight ? Math.max(8, button.top - menuHeight - 4) : button.bottom + 4;
    this.menuTicket = ticket;
  }

  closeTicket(ticket: Ticket): void {
    this.menuTicket = null;
    const updated: Ticket = { ...ticket, status: 'Closed' };
    this.ticketService.updateTicket(ticket.ticketId!, updated).subscribe({
      next: (saved) => {
        this.replaceTicket(saved);
        this.showToast('Ticket closed', '#' + saved.ticketId + ' has been closed.');
      },
      error: (error) => this.showToast('Could not close the ticket', UI.errorMessage(error, 'Please try again.'), 'err')
    });
  }

  // ---------- assign agent dialog ----------

  openAssign(ticket: Ticket): void {
    this.menuTicket = null;
    this.assignTicket = ticket;
    this.assignTab = 'suggested';
    this.assignSearch = '';
    this.actionError = '';
  }

  isMatch(agent: SupportAgent, ticket: Ticket): boolean {
    const expertiseList = MATCHING_EXPERTISE[ticket.issueCategory] || [];
    return expertiseList.includes(agent.expertise)
      || agent.expertise.toLowerCase().includes(ticket.issueCategory.toLowerCase());
  }

  // Suggested = available agents whose expertise matches the category. Manual = all available agents.
  get assignList(): SupportAgent[] {
    if (!this.assignTicket) { return []; }
    const text = this.assignSearch.trim().toLowerCase();
    const list: SupportAgent[] = [];
    for (const a of this.agents) {
      if (a.status !== 'Available') { continue; }
      if (this.assignTab === 'suggested' && !this.isMatch(a, this.assignTicket)) { continue; }
      if (text && !a.name.toLowerCase().includes(text) && !a.expertise.toLowerCase().includes(text)) { continue; }
      list.push(a);
    }
    return list;
  }

  countAvailable(suggestedOnly: boolean): number {
    let count = 0;
    for (const a of this.agents) {
      if (a.status === 'Available' && (!suggestedOnly || (this.assignTicket && this.isMatch(a, this.assignTicket)))) {
        count++;
      }
    }
    return count;
  }

  assign(agent: SupportAgent): void {
    if (!this.assignTicket) { return; }
    const ticket = this.assignTicket;
    const updated: Ticket = { ...ticket, supportAgent: { ...agent } };
    this.ticketService.updateTicket(ticket.ticketId!, updated).subscribe({
      next: (saved) => {
        this.replaceTicket(saved);
        this.assignTicket = null;
        this.showToast('Agent assigned', agent.name + ' is now working on #' + saved.ticketId + '.');
      },
      error: (error) => this.actionError = UI.errorMessage(error, 'Could not assign the agent.')
    });
  }

  // ---------- helpers ----------

  ticketsRaisedBy(userId?: number): number {
    let count = 0;
    for (const t of this.tickets) {
      if (t.user?.userId === userId) { count++; }
    }
    return count;
  }

  private replaceTicket(saved: Ticket): void {
    for (let i = 0; i < this.tickets.length; i++) {
      if (this.tickets[i].ticketId === saved.ticketId) {
        this.tickets[i] = saved;
      }
    }
  }

  private showToast(title: string, text: string, kind = 'ok'): void {
    this.toastKind = kind;
    this.toastTitle = title;
    this.toastText = text;
    setTimeout(() => this.toastTitle = '', 4200);
  }
}
