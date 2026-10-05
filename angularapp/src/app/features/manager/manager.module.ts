import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { ManagerDashboardComponent } from '../../components/manager-dashboard/manager-dashboard.component';
import { ManagerViewAgentsComponent } from '../../components/manager-view-agents/manager-view-agents.component';
import { ManagerViewTicketsComponent } from '../../components/manager-view-tickets/manager-view-tickets.component';
import { ManagerviewfeedbackComponent } from '../../components/managerviewfeedback/managerviewfeedback.component';
import { SupportAgentManagementComponent } from '../../components/support-agent-management/support-agent-management.component';
import { SharedModule } from '../../shared/shared.module';

/** Lazy-loaded Manager area. The parent route applies AuthGuard with data { role: 'Manager' }. */
const routes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'dashboard', component: ManagerDashboardComponent },
  { path: 'agents', component: ManagerViewAgentsComponent },
  { path: 'agents/add', component: SupportAgentManagementComponent },
  { path: 'agents/edit/:id', component: SupportAgentManagementComponent },
  { path: 'tickets', component: ManagerViewTicketsComponent },
  { path: 'feedbacks', component: ManagerviewfeedbackComponent }
];

@NgModule({
  declarations: [
    ManagerDashboardComponent,
    ManagerViewAgentsComponent,
    SupportAgentManagementComponent,
    ManagerViewTicketsComponent,
    ManagerviewfeedbackComponent
  ],
  imports: [SharedModule, RouterModule.forChild(routes)]
})
export class ManagerModule {}
