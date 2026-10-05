import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ISSUE_CATEGORIES, PRIORITIES, TICKET_STATUS } from '../../constants/constant';
import { Ticket } from '../../models/ticket.model';
import { AuthService } from '../../services/auth.service';
import { ErrorHandlerService } from '../../services/error-handler.service';
import { TicketService } from '../../services/ticket.service';

/** Client: "Create New Ticket" (/client/tickets/add) and "Edit Ticket" (/client/tickets/edit/:id). */
@Component({
  selector: 'app-ticket-management',
  templateUrl: './ticket-management.component.html',
  styleUrls: ['./ticket-management.component.css']
})
export class TicketManagementComponent implements OnInit {

  readonly priorities = PRIORITIES;
  readonly issueCategories = ISSUE_CATEGORIES;
  ticketForm: FormGroup;
  submitted = false;
  loading = false;
  isEditMode = false;
  formMessage = '';
  successMessage = '';
  private ticketId: number | null = null;
  private existingTicket: Ticket | null = null;

  constructor(
    private readonly fb: FormBuilder,
    private readonly ticketService: TicketService,
    private readonly authService: AuthService,
    private readonly errorHandler: ErrorHandlerService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {
    this.ticketForm = this.fb.group({
      title: ['', [Validators.required, Validators.maxLength(100)]],
      description: ['', [Validators.required, Validators.maxLength(2000)]],
      priority: ['', Validators.required],
      issueCategory: ['', Validators.required]
    });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEditMode = true;
      this.ticketId = Number(id);
      this.loadTicket(this.ticketId);
    }
  }

  showError(field: string): boolean {
    const control = this.ticketForm.get(field);
    return !!control && control.invalid && (control.touched || this.submitted);
  }

  hasError(field: string, error: string): boolean {
    return !!this.ticketForm.get(field)?.hasError(error);
  }

  onSubmit(): void {
    this.submitted = true;
    this.formMessage = '';
    if (this.ticketForm.invalid) {
      this.ticketForm.markAllAsTouched();
      this.formMessage = 'Please fill in all required fields correctly.';
      return;
    }
    this.loading = true;
    const ticket = this.buildTicket();
    const request$ = this.isEditMode && this.ticketId !== null
      ? this.ticketService.updateTicket(this.ticketId, ticket)
      : this.ticketService.addTicket(ticket);

    request$.subscribe({
      next: () => {
        this.loading = false;
        this.successMessage = this.isEditMode ? 'Ticket Updated Successfully!' : 'Ticket Added Successfully!';
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
      this.router.navigate(['/client/tickets']);
    } else {
      this.submitted = false;
      this.ticketForm.reset({ title: '', description: '', priority: '', issueCategory: '' });
    }
  }

  goBack(): void {
    this.router.navigate(['/client/tickets']);
  }

  private loadTicket(ticketId: number): void {
    this.ticketService.getTicketById(ticketId).subscribe({
      next: ticket => {
        if (ticket.status !== TICKET_STATUS.OPEN || ticket.agentId) {
          this.formMessage = 'Only open tickets without an assigned agent can be edited.';
          this.ticketForm.disable();
        }
        this.existingTicket = ticket;
        this.ticketForm.patchValue({
          title: ticket.title,
          description: ticket.description,
          priority: ticket.priority,
          issueCategory: ticket.issueCategory
        });
      },
      error: (err: HttpErrorResponse) => {
        this.formMessage = this.errorHandler.getMessage(err);
        this.ticketForm.disable();
      }
    });
  }

  private buildTicket(): Ticket {
    const value = this.ticketForm.value;
    const base: Ticket = this.existingTicket ?? {
      title: '', description: '', priority: '', issueCategory: '',
      status: TICKET_STATUS.OPEN, createdDate: new Date(), userId: this.authService.getUserId() as number
    };
    return {
      ticketId: base.ticketId,
      title: (value.title as string).trim(),
      description: (value.description as string).trim(),
      priority: value.priority as string,
      issueCategory: value.issueCategory as string,
      status: base.status,
      createdDate: base.createdDate,
      resolutionDate: base.resolutionDate,
      resolutionSummary: base.resolutionSummary,
      satisfied: base.satisfied,
      userId: base.userId,
      agentId: base.agentId
    };
  }
}
