import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AGENT_STATUS, EXPERTISE_OPTIONS, PROFILE_MAX_BYTES, VALIDATION_PATTERNS } from '../../constants/constant';
import { SupportAgent } from '../../models/support-agent.model';
import { ErrorHandlerService } from '../../services/error-handler.service';
import { SupportAgentService } from '../../services/support-agent.service';

/** Manager: "Add New Support Agent" (/manager/agents/add) and "Edit Support Agent" (/manager/agents/edit/:id). */
@Component({
  selector: 'app-support-agent-management',
  templateUrl: './support-agent-management.component.html',
  styleUrls: ['./support-agent-management.component.css']
})
export class SupportAgentManagementComponent implements OnInit {

  readonly expertiseOptions = EXPERTISE_OPTIONS;
  agentForm: FormGroup;
  submitted = false;
  loading = false;
  isEditMode = false;
  formMessage = '';
  successMessage = '';
  profileError = '';
  profileFileName = '';
  private agentId: number | null = null;
  private existingAgent: SupportAgent | null = null;

  constructor(
    private readonly fb: FormBuilder,
    private readonly agentService: SupportAgentService,
    private readonly errorHandler: ErrorHandlerService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {
    this.agentForm = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.pattern(VALIDATION_PATTERNS.EMAIL)]],
      phone: ['', [Validators.required, Validators.pattern(VALIDATION_PATTERNS.MOBILE)]],
      expertise: ['', Validators.required],
      experience: ['', [Validators.required, Validators.maxLength(50)]],
      shiftTiming: ['', [Validators.required, Validators.maxLength(50)]],
      remarks: ['', Validators.maxLength(1000)],
      profile: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode = true;
      this.agentId = Number(id);
      this.agentForm.get('profile')?.clearValidators();   // keep the stored image unless a new one is chosen
      this.agentForm.get('profile')?.updateValueAndValidity();
      this.loadAgent(this.agentId);
    }
  }

  showError(field: string): boolean {
    const control = this.agentForm.get(field);
    return !!control && control.invalid && (control.touched || this.submitted);
  }

  hasError(field: string, error: string): boolean {
    return !!this.agentForm.get(field)?.hasError(error);
  }

  onFileSelected(event: Event): void {
    this.profileError = '';
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    if (!file.type.startsWith('image/')) {
      this.profileError = 'Please choose an image file.';
      input.value = '';
      return;
    }
    if (file.size > PROFILE_MAX_BYTES) {
      this.profileError = 'Profile image must be 1 MB or smaller.';
      input.value = '';
      return;
    }
    this.profileFileName = file.name;
    const reader = new FileReader();
    reader.onload = () => {
      this.agentForm.patchValue({ profile: reader.result as string });   // Base64 data URL
      this.agentForm.get('profile')?.markAsTouched();
    };
    reader.readAsDataURL(file);
  }

  onSubmit(): void {
    this.submitted = true;
    this.formMessage = '';
    if (this.agentForm.invalid || this.profileError) {
      this.agentForm.markAllAsTouched();
      this.formMessage = 'Please fill in all required fields correctly.';
      return;
    }
    this.loading = true;
    const agent = this.buildAgent();
    const request$ = this.isEditMode && this.agentId !== null
      ? this.agentService.updateAgent(this.agentId, agent)
      : this.agentService.addAgent(agent);

    request$.subscribe({
      next: () => {
        this.loading = false;
        this.successMessage = this.isEditMode
          ? 'Support Agent Updated Successfully!' : 'Support Agent Added Successfully!';
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.formMessage = this.errorHandler.getMessage(err);
      }
    });
  }

  onSuccessOk(): void {
    this.successMessage = '';
    if (this.isEditMode) {
      this.router.navigate(['/manager/agents']);
      return;
    }
    this.submitted = false;
    this.profileFileName = '';
    this.agentForm.reset({
      name: '', email: '', phone: '', expertise: '', experience: '', shiftTiming: '', remarks: '', profile: ''
    });
    const fileInput = document.getElementById('profile') as HTMLInputElement | null;
    if (fileInput) {
      fileInput.value = '';
    }
  }

  goBack(): void {
    this.router.navigate(['/manager/agents']);
  }

  private loadAgent(agentId: number): void {
    this.agentService.getAgentById(agentId).subscribe({
      next: agent => {
        this.existingAgent = agent;
        this.agentForm.patchValue({
          name: agent.name,
          email: agent.email,
          phone: agent.phone,
          expertise: agent.expertise,
          experience: agent.experience,
          shiftTiming: agent.shiftTiming,
          remarks: agent.remarks ?? '',
          profile: agent.profile ?? ''
        });
      },
      error: (err: HttpErrorResponse) => {
        this.formMessage = this.errorHandler.getMessage(err);
        this.agentForm.disable();
      }
    });
  }

  private buildAgent(): SupportAgent {
    const v = this.agentForm.value;
    return {
      agentId: this.existingAgent?.agentId,
      name: (v.name as string).trim(),
      email: (v.email as string).trim(),
      phone: (v.phone as string).trim(),
      expertise: v.expertise as string,
      experience: String(v.experience).trim(),
      status: this.existingAgent?.status ?? AGENT_STATUS.AVAILABLE,
      addedDate: this.existingAgent?.addedDate ?? new Date(),
      profile: v.profile as string,
      shiftTiming: (v.shiftTiming as string).trim(),
      remarks: ((v.remarks as string) ?? '').trim()
    };
  }
}
