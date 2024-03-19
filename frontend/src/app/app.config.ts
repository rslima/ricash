import {APP_INITIALIZER, ApplicationConfig} from '@angular/core';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import {provideHttpClient} from "@angular/common/http";
import {AuthConfig, OAuthService, provideOAuthClient} from "angular-oauth2-oidc";


export const authCodeFlowConfig: AuthConfig = {
  clientId: 'ricash-frontend',
  issuer: 'http://localhost:9080/realms/Ricash',
  tokenEndpoint: 'http://localhost:9080/realms/Ricash/protocol/openid-connect/token',
  redirectUri: window.location.origin,
  responseType: 'code',
  scope: 'openid profile email',
}

function initializeOAuth(oauthService: OAuthService): Promise<void> {
  return new Promise((resolve, reject) => {
    oauthService.configure(authCodeFlowConfig);
    oauthService.setupAutomaticSilentRefresh();
    oauthService.loadDiscoveryDocumentAndLogin().then(() => {
      resolve();
    });
  });
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(),
    provideOAuthClient(),
    {
      provide: APP_INITIALIZER,
      useFactory: (oauthService: OAuthService) => () => initializeOAuth(oauthService),
      multi: true,
      deps: [
        OAuthService
      ]
    }
  ]
};
