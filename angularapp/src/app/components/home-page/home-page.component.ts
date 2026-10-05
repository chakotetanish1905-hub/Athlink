import { Component } from '@angular/core';

@Component({
  selector: 'app-home-page',
  templateUrl: './home-page.component.html',
  styleUrls: ['./home-page.component.css']
})
export class HomePageComponent {
  readonly supportPhone = '987-654-3210';
  readonly supportEmail = 'support@SupportSphere.com';
}
