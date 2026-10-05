import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { ClientViewTicketsComponent } from './components/client-view-tickets/client-view-tickets.component';
import { ClientnavComponent } from './components/clientnav/clientnav.component';
import { ClientpostfeedbackComponent } from './components/clientpostfeedback/clientpostfeedback.component';
import { ClientviewfeedbackComponent } from './components/clientviewfeedback/clientviewfeedback.component';
import { ErrorComponent } from './components/error/error.component';
import { HomePageComponent } from './components/home-page/home-page.component';
import { LoginComponent } from './components/login/login.component';
import { ManagerDashboardComponent } from './components/manager-dashboard/manager-dashboard.component';
import { ManagerViewAgentsComponent } from './components/manager-view-agents/manager-view-agents.component';
import { ManagerViewTicketsComponent } from './components/manager-view-tickets/manager-view-tickets.component';
import { ManagernavComponent } from './components/managernav/managernav.component';
import { ManagerviewfeedbackComponent } from './components/managerviewfeedback/managerviewfeedback.component';
import { SignupComponent } from './components/signup/signup.component';
import { SupportAgentManagementComponent } from './components/support-agent-management/support-agent-management.component';
import { SupportedAgentsComponent } from './components/supported-agents/supported-agents.component';
import { TicketDetailsComponent } from './components/ticket-details/ticket-details.component';
import { TicketManagementComponent } from './components/ticket-management/ticket-management.component';
import { AuthInterceptor } from './interceptors/auth.interceptor';

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    SignupComponent,
    HomePageComponent,
    ManagernavComponent,
    ClientnavComponent,
    ManagerDashboardComponent,
    ManagerViewTicketsComponent,
    ManagerViewAgentsComponent,
    SupportAgentManagementComponent,
    ManagerviewfeedbackComponent,
    ClientViewTicketsComponent,
    TicketManagementComponent,
    TicketDetailsComponent,
    SupportedAgentsComponent,
    ClientpostfeedbackComponent,
    ClientviewfeedbackComponent,
    ErrorComponent
  ],
  imports: [BrowserModule, HttpClientModule, FormsModule, AppRoutingModule],
  providers: [{ provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }],
  bootstrap: [AppComponent]
})
export class AppModule {}
