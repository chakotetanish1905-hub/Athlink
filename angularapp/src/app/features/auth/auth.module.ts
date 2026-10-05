import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { LoginComponent } from '../../components/login/login.component';
import { SignupComponent } from '../../components/signup/signup.component';
import { SharedModule } from '../../shared/shared.module';

const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'signup', component: SignupComponent }
];

/** Lazy-loaded public pages. */
@NgModule({
  declarations: [LoginComponent, SignupComponent],
  imports: [SharedModule, RouterModule.forChild(routes)]
})
export class AuthModule {}
