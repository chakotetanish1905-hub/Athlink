import { Component, OnInit } from '@angular/core';
import { SupportAgent } from '../../models/support-agent.model';
import { SupportAgentService } from '../../services/support-agent.service';
import { UI } from '../ui-helpers';

@Component({
  selector: 'app-manager-view-agents',
  templateUrl: './manager-view-agents.component.html',
  styleUrls: ['./manager-view-agents.component.css']
})
export class ManagerViewAgentsComponent implements OnInit {

  ui = UI;
  agents: SupportAgent[] = [];
  loading = true;
  errorMessage = '';

  // Search by name and expertise, filter by status (SRS)
  nameSearch = '';
  expertiseSearch = '';
  statusFilter = '';
  view = 'table';

  viewAgent: SupportAgent | null = null;
  deleteAgent: SupportAgent | null = null;
  deleteError = '';

  toastTitle = '';
  toastText = '';
  toastKind = 'ok';

  constructor(private agentService: SupportAgentService) {}

  ngOnInit(): void {
    this.loadAgents();
  }

  loadAgents(): void {
    this.loading = true;
    this.errorMessage = '';
    this.agentService.getAllAgents().subscribe({
      next: (agents) => {
        this.agents = agents;
        this.loading = false;
      },
      error: (error) => {
        this.loading = false;
        this.errorMessage = UI.errorMessage(error, 'Could not load support agents.');
      }
    });
  }

  get filteredAgents(): SupportAgent[] {
    const name = this.nameSearch.trim().toLowerCase();
    const expertise = this.expertiseSearch.trim().toLowerCase();
    const result: SupportAgent[] = [];
    for (const a of this.agents) {
      if ((!name || a.name.toLowerCase().includes(name))
          && (!expertise || a.expertise.toLowerCase().includes(expertise))
          && (!this.statusFilter || a.status === this.statusFilter)) {
        result.push(a);
      }
    }
    return result;
  }

  get availableCount(): number {
    let count = 0;
    for (const a of this.agents) {
      if (a.status === 'Available') { count++; }
    }
    return count;
  }

  get anyFilter(): boolean {
    return !!(this.nameSearch || this.expertiseSearch || this.statusFilter);
  }

  clearFilters(): void {
    this.nameSearch = '';
    this.expertiseSearch = '';
    this.statusFilter = '';
  }

  // "Make Available" / "Make Unavailable"
  toggleStatus(agent: SupportAgent): void {
    const newStatus = agent.status === 'Available' ? 'Unavailable' : 'Available';
    const updated: SupportAgent = { ...agent, status: newStatus };
    this.agentService.updateAgent(agent.agentId!, updated).subscribe({
      next: (saved) => {
        agent.status = saved.status;
        this.showToast(agent.name + ' is now ' + saved.status, 'Availability updated.', 'info');
      },
      error: (error) => this.showToast('Could not update availability', UI.errorMessage(error, 'Please try again.'), 'err')
    });
  }

  askDelete(agent: SupportAgent): void {
    this.deleteError = '';
    this.deleteAgent = agent;
  }

  confirmDelete(): void {
    if (!this.deleteAgent) { return; }
    const agent = this.deleteAgent;
    this.agentService.deleteAgent(agent.agentId!).subscribe({
      next: () => {
        this.deleteAgent = null;
        this.agents = this.agents.filter(a => a.agentId !== agent.agentId);
        this.showToast('Support agent deleted', agent.name + ' was removed.', 'ok');
      },
      // For example AgentDeletionException (409) when the agent still has tickets
      error: (error) => this.deleteError = UI.errorMessage(error, 'Could not delete the agent.')
    });
  }

  private showToast(title: string, text: string, kind: string): void {
    this.toastKind = kind;
    this.toastTitle = title;
    this.toastText = text;
    setTimeout(() => this.toastTitle = '', 4200);
  }
}
