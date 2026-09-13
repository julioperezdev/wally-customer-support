import { describe, expect, it } from "vitest";
import { buildCognitoAuthorizationUrl, isCognitoConfigured, type CognitoConfiguration } from "./cognito";

const configuration: CognitoConfiguration = {
  authority: "https://wcs-test.auth.us-east-1.amazoncognito.com",
  clientId: "client-id",
  redirectUri: "http://localhost:5173/auth/callback",
  logoutUri: "http://localhost:5173/",
  enabled: true
};

describe("Cognito PKCE client", () => {
  it("builds an authorization-code URL without a client secret", () => {
    const url = new URL(buildCognitoAuthorizationUrl(configuration, "state-1", "challenge-1"));

    expect(url.origin).toBe(configuration.authority);
    expect(url.pathname).toBe("/oauth2/authorize");
    expect(url.searchParams.get("response_type")).toBe("code");
    expect(url.searchParams.get("client_id")).toBe("client-id");
    expect(url.searchParams.get("code_challenge_method")).toBe("S256");
    expect(url.searchParams.get("code_challenge")).toBe("challenge-1");
    expect(url.searchParams.get("scope")).toBe("openid email profile");
    expect(url.search).not.toContain("secret");
  });

  it("does not enable login when Cognito is only partially configured", () => {
    expect(isCognitoConfigured({ ...configuration, clientId: "" })).toBe(false);
    expect(isCognitoConfigured({ ...configuration, enabled: false })).toBe(false);
    expect(isCognitoConfigured(configuration)).toBe(true);
  });
});
