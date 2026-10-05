import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './components/authguard/authguard.guard';
import { ClientViewTicketsComponent } from './components/client-view-tickets/client-view-tickets.component';
import { ClientpostfeedbackComponent } from './components/clientpostfeedback/clientpostfeedback.component';
import { ClientviewfeedbackComponent } from './components/clientviewfeedback/clientviewfeedback.component';
import { ErrorComponent } from './components/error/error.component';
import { HomePageComponent } from './components/home-page/home-page.component';
import { LoginComponent } from './components/login/login.component';
import { ManagerDashboardComponent } from './components/manager-dashboard/manager-dashboard.component';
import { ManagerViewAgentsComponent } from './components/manager-view-agents/manager-view-agents.component';
import { ManagerViewTicketsComponent } from './components/manager-view-tickets/manager-view-tickets.component';
import { ManagerviewfeedbackComponent } from './components/managerviewfeedback/managerviewfeedback.component';
import { SignupComponent } from './components/signup/signup.component';
import { SupportAgentManagementComponent } from './components/support-agent-management/support-agent-management.component';
import { SupportedAgentsComponent } from './components/supported-agents/supported-agents.component';
import { TicketDetailsComponent } from './components/ticket-details/ticket-details.component';
import { TicketManagementComponent } from './components/ticket-management/ticket-management.component';

const manager = { role: 'Manager' };
const client = { role: 'Client' };

const routes: Routes = [
  // The login page is the first page of the application
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'signup', component: SignupComponent },

  // Both roles
  { path: 'home', component: HomePageComponent, canActivate: [AuthGuard] },

  // Manager pages
  { path: 'manager/dashboard', component: ManagerDashboardComponent, canActivate: [AuthGuard], data: manager },
  { path: 'manager/tickets', component: ManagerViewTicketsComponent, canActivate: [AuthGuard], data: manager },
  { path: 'manager/tickets/:id', component: TicketDetailsComponent, canActivate: [AuthGuard], data: manager },
  { path: 'manager/agents', component: ManagerViewAgentsComponent, canActivate: [AuthGuard], data: manager },
  { path: 'manager/agents/add', component: SupportAgentManagementComponent, canActivate: [AuthGuard], data: manager },
  { path: 'manager/agents/edit/:id', component: SupportAgentManagementComponent, canActivate: [AuthGuard], data: manager },
  { path: 'manager/feedbacks', component: ManagerviewfeedbackComponent, canActivate: [AuthGuard], data: manager },

  // Client pages
  { path: 'client/tickets', component: ClientViewTicketsComponent, canActivate: [AuthGuard], data: client },
  { path: 'client/tickets/add', component: TicketManagementComponent, canActivate: [AuthGuard], data: client },
  { path: 'client/tickets/edit/:id', component: TicketManagementComponent, canActivate: [AuthGuard], data: client },
  { path: 'client/tickets/:id', component: TicketDetailsComponent, canActivate: [AuthGuard], data: client },
  { path: 'client/agents', component: SupportedAgentsComponent, canActivate: [AuthGuard], data: client },
  { path: 'client/feedback/add', component: ClientpostfeedbackComponent, canActivate: [AuthGuard], data: client },
  { path: 'client/feedbacks', component: ClientviewfeedbackComponent, canActivate: [AuthGuard], data: client },

  // Error pages
  { path: 'error', component: ErrorComponent },
  { path: '**', component: ErrorComponent }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
