import { Component, OnInit } from '@angular/core';
import { SupportAgent } from '../../models/support-agent.model';
import { Ticket } from '../../models/ticket.model';
import { AuthService } from '../../services/auth.service';
import { SupportAgentService } from '../../services/support-agent.service';
import { TicketService } from '../../services/ticket.service';
import { UI } from '../ui-helpers';

interface ChartDay {
  label: string;      // x-axis label (every second day)
  fullDate: string;   // tooltip
  created: number;
  resolved: number;
}

interface StatusRow {
  status: string;
  color: string;
  count: number;
}

// Everything on this page is calculated from GET /api/ticket and GET /api/supportAgent.
@Component({
  selector: 'app-manager-dashboard',
  templateUrl: './manager-dashboard.component.html',
  styleUrls: ['./manager-dashboard.component.css']
})
export class ManagerDashboardComponent implements OnInit {

  ui = UI;
  loading = true;
  errorMessage = '';
  greeting = '';
  firstName = '';
  todayLabel = '';

  tickets: Ticket[] = [];
  agents: SupportAgent[] = [];
  recentTickets: Ticket[] = [];

  // KPI numbers
  openCount = 0;
  inProgressCount = 0;
  resolvedCount = 0;
  closedCount = 0;
  unassignedCount = 0;
  availableAgents = 0;
  createdLast7 = 0;
  resolvedLast7 = 0;
  trendPercent = 0;

  // Chart + status distribution
  chartDays: ChartDay[] = [];
  chartMax = 4;
  yAxis: number[] = [];
  statusRows: StatusRow[] = [];

  // Client satisfaction (happy / unhappy clients)
  happyCount = 0;
  unhappyCount = 0;
  awaitingCount = 0;

  constructor(private ticketService: TicketService, private agentService: SupportAgentService,
              private authService: AuthService) {}

  ngOnInit(): void {
    this.firstName = this.authService.getUsername().split(' ')[0];
    const hour = new Date().getHours();
    this.greeting = hour < 12 ? 'Good morning' : (hour < 17 ? 'Good afternoon' : 'Good evening');
    this.todayLabel = UI.formatDate(UI.todayIso());
    this.loadData();
  }

  loadData(): void {
    this.loading = true;
    this.errorMessage = '';
    this.ticketService.getAllTickets().subscribe({
      next: (tickets) => {
        this.tickets = tickets;
        this.agentService.getAllAgents().subscribe({
          next: (agents) => {
            this.agents = agents;
            this.calculate();
            this.loading = false;
          },
          error: (error) => this.showError(error)
        });
      },
      error: (error) => this.showError(error)
    });
  }

  percent(count: number): string {
    if (this.tickets.length === 0) { return '0%'; }
    return Math.round(count / this.tickets.length * 100) + '%';
  }

  barHeight(value: number): string {
    return (value / this.chartMax * 100) + '%';
  }

  get satisfactionPercent(): string {
    const rated = this.happyCount + this.unhappyCount;
    return rated ? Math.round(this.happyCount / rated * 100) + '%' : '—';
  }

  get happyWidth(): string {
    const rated = this.happyCount + this.unhappyCount;
    return rated ? (this.happyCount / rated * 100) + '%' : '0%';
  }

  get unhappyWidth(): string {
    const rated = this.happyCount + this.unhappyCount;
    return rated ? (this.unhappyCount / rated * 100) + '%' : '0%';
  }

  private showError(error: any): void {
    this.loading = false;
    this.errorMessage = UI.errorMessage(error, 'Could not load the dashboard.');
  }

  private calculate(): void {
    const today = UI.todayIso();
    const sevenDaysAgo = this.addDays(today, -7);
    const fourteenDaysAgo = this.addDays(today, -14);
    let createdPrevious7 = 0;

    this.openCount = 0; this.inProgressCount = 0; this.resolvedCount = 0; this.closedCount = 0;
    this.unassignedCount = 0; this.createdLast7 = 0; this.resolvedLast7 = 0;
    this.happyCount = 0; this.unhappyCount = 0; this.awaitingCount = 0;

    for (const t of this.tickets) {
      if (t.status === 'Open') { this.openCount++; }
      if (t.status === 'In Progress') { this.inProgressCount++; }
      if (t.status === 'Resolved') { this.resolvedCount++; }
      if (t.status === 'Closed') { this.closedCount++; }
      if (t.status === 'Open' && !t.supportAgent) { this.unassignedCount++; }

      const created = t.createdDate || '';
      if (created > sevenDaysAgo) { this.createdLast7++; }
      if (created <= sevenDaysAgo && created > fourteenDaysAgo) { createdPrevious7++; }
      if (t.resolutionDate && t.resolutionDate > sevenDaysAgo) { this.resolvedLast7++; }

      if (UI.isDone(t)) {
        if (t.satisfied === true) { this.happyCount++; }
        else if (t.satisfied === false) { this.unhappyCount++; }
        else { this.awaitingCount++; }
      }
    }

    this.trendPercent = createdPrevious7 ? Math.round((this.createdLast7 - createdPrevious7) / createdPrevious7 * 100) : 0;

    this.availableAgents = 0;
    for (const a of this.agents) {
      if (a.status === 'Available') { this.availableAgents++; }
    }

    this.statusRows = [
      { status: 'Open', color: '#4F46E5', count: this.openCount },
      { status: 'In Progress', color: '#F59E0B', count: this.inProgressCount },
      { status: 'Resolved', color: '#16A34A', count: this.resolvedCount },
      { status: 'Closed', color: '#94A3B8', count: this.closedCount }
    ];

    // Created vs. resolved per day for the last 14 days
    this.chartDays = [];
    let highest = 1;
    for (let i = 13; i >= 0; i--) {
      const day = this.addDays(today, -i);
      let created = 0;
      let resolved = 0;
      for (const t of this.tickets) {
        if (t.createdDate === day) { created++; }
        if (t.resolutionDate === day) { resolved++; }
      }
      highest = Math.max(highest, created, resolved);
      this.chartDays.push({ label: i % 2 === 0 ? UI.shortDate(day) : '', fullDate: UI.formatDate(day), created, resolved });
    }
    this.chartMax = Math.max(4, Math.ceil(highest / 4) * 4);
    this.yAxis = [this.chartMax, this.chartMax * 0.75, this.chartMax * 0.5, this.chartMax * 0.25, 0];

    this.recentTickets = this.tickets.slice().sort(UI.newestFirst).slice(0, 6);
  }

  private addDays(isoDate: string, days: number): string {
    const d = new Date(isoDate + 'T00:00:00Z');
    d.setUTCDate(d.getUTCDate() + days);
    return d.toISOString().substring(0, 10);
  }
}
