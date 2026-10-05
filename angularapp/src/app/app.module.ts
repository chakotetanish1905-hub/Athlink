import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';

import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { ClientnavComponent } from './components/clientnav/clientnav.component';
import { ErrorComponent } from './components/error/error.component';
import { HomePageComponent } from './components/home-page/home-page.component';
import { ManagernavComponent } from './components/managernav/managernav.component';
import { ToastComponent } from './components/toast/toast.component';
import { AuthInterceptor } from './interceptors/auth.interceptor';
import { SharedModule } from './shared/shared.module';

/**
 * Eager shell: navigation bars, home page, error page and toasts.
 * Auth, Manager and Client features are lazy-loaded (see app-routing.module.ts).
 */
@NgModule({
  declarations: [
    AppComponent,
    ManagernavComponent,
    ClientnavComponent,
    HomePageComponent,
    ErrorComponent,
    ToastComponent
  ],
  imports: [BrowserModule, HttpClientModule, SharedModule, AppRoutingModule],
  providers: [{ provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true }],
  bootstrap: [AppComponent]
})
export class AppModule {}
