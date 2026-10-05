import { Component, OnInit } from '@angular/core';
import { NgForm } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Ticket } from '../../models/ticket.model';
import { TicketService } from '../../services/ticket.service';
import { UI } from '../ui-helpers';

// Create a ticket, or edit one when the URL has an id (/client/tickets/edit/:id).
@Component({
  selector: 'app-ticket-management',
  templateUrl: './ticket-management.component.html',
  styleUrls: ['./ticket-management.component.css']
})
export class TicketManagementComponent implements OnInit {

  ticket: Ticket = this.emptyTicket();
  editId: number | null = null;
  submitted = false;
  saving = false;
  errorMessage = '';
  successMessage = '';

  priorities = [
    { value: 'High', hint: 'Work is blocked or many users affected', cls: 'p-high' },
    { value: 'Medium', hint: 'Work is impacted, a workaround exists', cls: 'p-medium' },
    { value: 'Low', hint: 'Question or minor inconvenience', cls: 'p-low' }
  ];
  categories = [
    { value: 'Technical', hint: 'Login, errors, performance' },
    { value: 'Billing', hint: 'Invoices, payments, refunds' },
    { value: 'General', hint: 'Account and how-to questions' }
  ];

  constructor(private ticketService: TicketService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.editId = Number(id);
      this.ticketService.getTicketById(this.editId).subscribe({
        next: (ticket) => this.ticket = ticket,
        error: (error) => this.errorMessage = UI.errorMessage(error, 'Could not load this ticket.')
      });
    }
  }

  onSubmit(form: NgForm): void {
    this.submitted = true;
    this.errorMessage = '';
    if (form.invalid || !this.ticket.priority || !this.ticket.issueCategory) {
      return;
    }

    this.saving = true;
    this.ticket.title = this.ticket.title.trim();
    this.ticket.description = this.ticket.description.trim();

    if (this.editId) {
      this.ticketService.updateTicket(this.editId, this.ticket).subscribe({
        next: () => {
          this.saving = false;
          this.successMessage = 'Ticket Updated Successfully!';
        },
        error: (error) => this.showError(error)
      });
    } else {
      this.ticketService.addTicket(this.ticket).subscribe({
        next: () => {
          this.saving = false;
          this.successMessage = 'Ticket Added Successfully!';
          form.resetForm();
          this.ticket = this.emptyTicket();
          this.submitted = false;
        },
        error: (error) => this.showError(error)
      });
    }
  }

  // After "Ok": add -> the form is ready for a new ticket, edit -> back to the list (SRS)
  closeSuccess(): void {
    this.successMessage = '';
    if (this.editId) {
      this.router.navigate(['/client/tickets']);
    }
  }

  private showError(error: any): void {
    this.saving = false;
    this.errorMessage = UI.errorMessage(error, 'Could not save the ticket.');
  }

  private emptyTicket(): Ticket {
    return { title: '', description: '', priority: '', issueCategory: '' };
  }
}
