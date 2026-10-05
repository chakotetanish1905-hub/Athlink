import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ClientViewTicketsComponent } from '../../components/client-view-tickets/client-view-tickets.component';
import { ClientpostfeedbackComponent } from '../../components/clientpostfeedback/clientpostfeedback.component';
import { ClientviewfeedbackComponent } from '../../components/clientviewfeedback/clientviewfeedback.component';
import { SupportedAgentsComponent } from '../../components/supported-agents/supported-agents.component';
import { TicketDetailsComponent } from '../../components/ticket-details/ticket-details.component';
import { TicketManagementComponent } from '../../components/ticket-management/ticket-management.component';
import { SharedModule } from '../../shared/shared.module';

/** Lazy-loaded Client area. The parent route applies AuthGuard with data { role: 'Client' }. */
const routes: Routes = [
  { path: '', redirectTo: 'tickets', pathMatch: 'full' },
  { path: 'tickets', component: ClientViewTicketsComponent },
  { path: 'tickets/add', component: TicketManagementComponent },
  { path: 'tickets/edit/:id', component: TicketManagementComponent },
  { path: 'tickets/:id', component: TicketDetailsComponent },
  { path: 'agents', component: SupportedAgentsComponent },
  { path: 'feedback/add/:ticketId', component: ClientpostfeedbackComponent },
  { path: 'feedbacks', component: ClientviewfeedbackComponent }
];

@NgModule({
  declarations: [
    TicketManagementComponent,
    ClientViewTicketsComponent,
    TicketDetailsComponent,
    SupportedAgentsComponent,
    ClientpostfeedbackComponent,
    ClientviewfeedbackComponent
  ],
  imports: [SharedModule, RouterModule.forChild(routes)]
})
export class ClientModule {}
