export type CognitoConfiguration = {
  authority: string;
  clientId: string;
  redirectUri: string;
  logoutUri: string;
  enabled: boolean;
};

type CognitoTokenResponse = {
  access_token?: string;
  error?: string;
  error_description?: string;
};

const STATE_KEY = "wcs.cognito.oauth.state";
const VERIFIER_KEY = "wcs.cognito.oauth.verifier";

export const cognitoConfiguration: CognitoConfiguration = {
  authority: (import.meta.env.VITE_WCS_COGNITO_AUTHORITY ?? "").replace(/\/+$/, ""),
  clientId: import.meta.env.VITE_WCS_COGNITO_CLIENT_ID ?? "",
  redirectUri: import.meta.env.VITE_WCS_COGNITO_REDIRECT_URI ?? `${window.location.origin}/auth/callback`,
  logoutUri: import.meta.env.VITE_WCS_COGNITO_LOGOUT_URI ?? `${window.location.origin}/`,
  enabled: import.meta.env.VITE_WCS_COGNITO_ENABLED === "true"
};

export function isCognitoConfigured(configuration = cognitoConfiguration): boolean {
  return configuration.enabled
    && Boolean(configuration.authority)
    && Boolean(configuration.clientId)
    && Boolean(configuration.redirectUri)
    && Boolean(configuration.logoutUri);
}

export function buildCognitoAuthorizationUrl(
  configuration: CognitoConfiguration,
  state: string,
  codeChallenge: string
): string {
  const params = new URLSearchParams({
    response_type: "code",
    client_id: configuration.clientId,
    redirect_uri: configuration.redirectUri,
    scope: "openid email profile",
    state,
    code_challenge_method: "S256",
    code_challenge: codeChallenge
  });
  return `${configuration.authority}/oauth2/authorize?${params.toString()}`;
}

export async function startCognitoLogin(configuration = cognitoConfiguration): Promise<void> {
  if (!isCognitoConfigured(configuration)) {
    throw new Error("Cognito no está configurado para este backoffice.");
  }
  const state = randomUrlValue(32);
  const verifier = randomUrlValue(64);
  const challenge = await codeChallenge(verifier);
  sessionStorage.setItem(STATE_KEY, state);
  sessionStorage.setItem(VERIFIER_KEY, verifier);
  window.location.assign(buildCognitoAuthorizationUrl(configuration, state, challenge));
}

export async function completeCognitoLogin(configuration = cognitoConfiguration): Promise<string | null> {
  if (!isCognitoConfigured(configuration)) return null;
  const params = new URLSearchParams(window.location.search);
  const code = params.get("code");
  if (!code) return null;

  const expectedState = sessionStorage.getItem(STATE_KEY);
  const receivedState = params.get("state");
  const verifier = sessionStorage.getItem(VERIFIER_KEY);
  if (!expectedState || !receivedState || expectedState !== receivedState || !verifier) {
    clearLoginState();
    throw new Error("La respuesta de Cognito no superó la validación de estado.");
  }

  const response = await fetch(`${configuration.authority}/oauth2/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      client_id: configuration.clientId,
      code,
      redirect_uri: configuration.redirectUri,
      code_verifier: verifier
    })
  });
  const payload = await response.json() as CognitoTokenResponse;
  clearLoginState();
  cleanCallbackUrl(configuration);
  if (!response.ok || !payload.access_token) {
    throw new Error(payload.error_description ?? payload.error ?? "Cognito no devolvió un access token.");
  }
  return payload.access_token;
}

export function logoutFromCognito(configuration = cognitoConfiguration): void {
  if (!isCognitoConfigured(configuration)) return;
  window.location.assign(`${configuration.authority}/logout?${new URLSearchParams({
    client_id: configuration.clientId,
    logout_uri: configuration.logoutUri
  }).toString()}`);
}

function clearLoginState(): void {
  sessionStorage.removeItem(STATE_KEY);
  sessionStorage.removeItem(VERIFIER_KEY);
}

function cleanCallbackUrl(configuration: CognitoConfiguration): void {
  const redirect = new URL(configuration.redirectUri, window.location.origin);
  window.history.replaceState({}, document.title, `${redirect.pathname}${redirect.hash}`);
}

function randomUrlValue(bytes: number): string {
  const values = new Uint8Array(bytes);
  crypto.getRandomValues(values);
  return base64Url(values);
}

async function codeChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return base64Url(new Uint8Array(digest));
}

function base64Url(values: Uint8Array): string {
  let binary = "";
  values.forEach((value) => { binary += String.fromCharCode(value); });
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}
