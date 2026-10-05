import { Component, OnInit } from '@angular/core';
import { NgForm } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { SupportAgent } from '../../models/support-agent.model';
import { SupportAgentService } from '../../services/support-agent.service';
import { UI } from '../ui-helpers';

// Add a new support agent, or edit one when the URL has an id (/manager/agents/edit/:id).
@Component({
  selector: 'app-support-agent-management',
  templateUrl: './support-agent-management.component.html',
  styleUrls: ['./support-agent-management.component.css']
})
export class SupportAgentManagementComponent implements OnInit {

  ui = UI;
  agent: SupportAgent = this.emptyAgent();
  editId: number | null = null;
  fileName = '';
  submitted = false;
  saving = false;
  errorMessage = '';
  successMessage = '';

  expertiseOptions = ['Technical Support', 'Software Support', 'Networking', 'Billing', 'General Support'];
  shiftOptions = ['6 AM - 3 PM', '9 AM - 6 PM', '10 AM - 7 PM', '12 PM - 9 PM', '3 PM - 12 AM'];

  constructor(private agentService: SupportAgentService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.editId = Number(id);
      this.agentService.getAgentById(this.editId).subscribe({
        next: (agent) => {
          this.agent = agent;
          this.fileName = agent.profile ? 'Existing file on record' : '';
        },
        error: (error) => this.errorMessage = UI.errorMessage(error, 'Could not load this agent.')
      });
    }
  }

  // Reads the chosen file and keeps it as a base64 string (SRS: profile is stored as base64)
  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files || input.files.length === 0) {
      return;
    }
    const file = input.files[0];
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = String(reader.result);
      this.agent.profile = dataUrl.substring(dataUrl.indexOf(',') + 1);
      this.fileName = file.name + ' · ' + Math.max(1, Math.round(file.size / 1024)) + ' KB';
    };
    reader.readAsDataURL(file);
  }

  onSubmit(form: NgForm): void {
    this.submitted = true;
    this.errorMessage = '';
    if (form.invalid) {
      return;
    }

    this.saving = true;
    if (this.editId) {
      this.agentService.updateAgent(this.editId, this.agent).subscribe({
        next: () => {
          this.saving = false;
          this.successMessage = 'Support Agent Updated Successfully!';
        },
        error: (error) => this.showError(error)
      });
    } else {
      this.agentService.addAgent(this.agent).subscribe({
        next: () => {
          this.saving = false;
          this.successMessage = 'Support Agent Added Successfully!';
          form.resetForm();
          this.agent = this.emptyAgent();
          this.fileName = '';
          this.submitted = false;
        },
        error: (error) => this.showError(error)
      });
    }
  }

  // After "Ok": add -> stay on the empty form, edit -> back to the list (SRS)
  closeSuccess(): void {
    this.successMessage = '';
    if (this.editId) {
      this.router.navigate(['/manager/agents']);
    }
  }

  private showError(error: any): void {
    this.saving = false;
    this.errorMessage = UI.errorMessage(error, 'Could not save the support agent.');
  }

  private emptyAgent(): SupportAgent {
    return { name: '', email: '', phone: '', expertise: '', experience: '', status: 'Available',
      profile: null, shiftTiming: '', remarks: '' };
  }
}
