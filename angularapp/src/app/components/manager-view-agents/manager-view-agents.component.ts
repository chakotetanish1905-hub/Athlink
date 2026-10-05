import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { AGENT_STATUS } from '../../constants/constant';
import { SupportAgent } from '../../models/support-agent.model';
import { ErrorHandlerService } from '../../services/error-handler.service';
import { NotificationService } from '../../services/notification.service';
import { SupportAgentService } from '../../services/support-agent.service';

/** Manager: "Support Agents" table with search, status filter, edit/delete/toggle/profile. */
@Component({
  selector: 'app-manager-view-agents',
  templateUrl: './manager-view-agents.component.html',
  styleUrls: ['./manager-view-agents.component.css']
})
export class ManagerViewAgentsComponent implements OnInit {

  readonly statuses = [AGENT_STATUS.AVAILABLE, AGENT_STATUS.UNAVAILABLE];
  agents: SupportAgent[] = [];
  searchText = '';
  statusFilter = '';
  loading = true;
  agentToDelete: SupportAgent | null = null;
  profileAgent: SupportAgent | null = null;

  constructor(
    private readonly agentService: SupportAgentService,
    private readonly notification: NotificationService,
    private readonly errorHandler: ErrorHandlerService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    this.loadAgents();
  }

  get filteredAgents(): SupportAgent[] {
    const term = this.searchText.trim().toLowerCase();
    return this.agents.filter(a =>
      (!term || a.name.toLowerCase().includes(term) || a.expertise.toLowerCase().includes(term)) &&
      (!this.statusFilter || a.status === this.statusFilter));
  }

  isAvailable(agent: SupportAgent): boolean {
    return agent.status === AGENT_STATUS.AVAILABLE;
  }

  editAgent(agent: SupportAgent): void {
    this.router.navigate(['/manager/agents/edit', agent.agentId]);
  }

  toggleStatus(agent: SupportAgent): void {
    if (!agent.agentId) {
      return;
    }
    const updated: SupportAgent = {
      ...agent,
      status: this.isAvailable(agent) ? AGENT_STATUS.UNAVAILABLE : AGENT_STATUS.AVAILABLE
    };
    this.agentService.updateAgent(agent.agentId, updated).subscribe({
      next: saved => {
        agent.status = saved.status;
        this.notification.success(`${saved.name} is now ${saved.status}.`);
      },
      error: (err: HttpErrorResponse) => this.notification.error(this.errorHandler.getMessage(err))
    });
  }

  confirmDelete(): void {
    const agent = this.agentToDelete;
    this.agentToDelete = null;
    if (!agent?.agentId) {
      return;
    }
    this.agentService.deleteAgent(agent.agentId).subscribe({
      next: () => {
        this.notification.success('Support agent deleted successfully.');
        this.loadAgents();
      },
      error: (err: HttpErrorResponse) => this.notification.error(this.errorHandler.getMessage(err))
    });
  }

  private loadAgents(): void {
    this.loading = true;
    this.agentService.getAllAgents().subscribe({
      next: agents => {
        this.agents = agents;
        this.loading = false;
      },
      error: () => {
        this.agents = [];
        this.loading = false;
      }
    });
  }
}
