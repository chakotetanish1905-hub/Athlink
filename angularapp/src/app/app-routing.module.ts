import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './components/authguard/authguard.guard';
import { ErrorComponent } from './components/error/error.component';
import { HomePageComponent } from './components/home-page/home-page.component';
import { ROLES } from './constants/constant';

const routes: Routes = [
  // Login is the first page rendered (SRS application assumption #1).
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: '', loadChildren: () => import('./features/auth/auth.module').then(m => m.AuthModule) },
  { path: 'home', component: HomePageComponent, canActivate: [AuthGuard] },
  {
    path: 'manager',
    canActivate: [AuthGuard],
    data: { role: ROLES.MANAGER },
    loadChildren: () => import('./features/manager/manager.module').then(m => m.ManagerModule)
  },
  {
    path: 'client',
    canActivate: [AuthGuard],
    data: { role: ROLES.CLIENT },
    loadChildren: () => import('./features/client/client.module').then(m => m.ClientModule)
  },
  { path: 'error', component: ErrorComponent },
  { path: 'error/:code', component: ErrorComponent },
  { path: '**', component: ErrorComponent, data: { code: '404' } }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule {}
