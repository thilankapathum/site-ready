import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import {
  SessionExpiredModalComponent
} from './pages/features/modals/session-expired-modal/session-expired-modal.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, SessionExpiredModalComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  title = 'site-ready';
}
