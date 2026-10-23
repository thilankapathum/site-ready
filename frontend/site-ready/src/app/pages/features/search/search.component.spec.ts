import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { SearchComponent } from './search.component';

describe('SearchComponent', () => {
  let component: SearchComponent;
  let fixture: ComponentFixture<SearchComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SearchComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {queryParamMap: of(convertToParamMap({siteId: 'BTAMP5', status: 'APPROVED', page: '2', size: '25'}))}
        },
      ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SearchComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('restores filters and pagination from the URL', () => {
    expect(component.filters.siteId).toBe('BTAMP5');
    expect(component.filters.status).toBe('APPROVED');
    expect(component.page()).toBe(2);
    expect(component.pageSize).toBe(25);

    const req = http.expectOne(r => r.url.endsWith('/search'));
    expect(req.request.params.get('siteId')).toBe('BTAMP5');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('25');
  });
});
