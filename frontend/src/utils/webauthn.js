function base64UrlToBuffer(base64url) {
  const padding = "=".repeat((4 - (base64url.length % 4)) % 4);
  const base64 = (base64url + padding).replace(/-/g, "+").replace(/_/g, "/");
  const raw = atob(base64);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
  return bytes.buffer;
}

function bufferToBase64Url(buffer) {
  const bytes = new Uint8Array(buffer);
  let str = "";
  for (const byte of bytes) str += String.fromCharCode(byte);
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function isWebAuthnSupported() {
  return typeof window !== "undefined" && !!window.PublicKeyCredential;
}

export async function registerBiometricCredential(options) {
  const publicKey = {
    ...options,
    challenge: base64UrlToBuffer(options.challenge),
    user: {
      ...options.user,
      id: base64UrlToBuffer(options.user.id),
    },
    excludeCredentials: (options.excludeCredentials || []).map((cred) => ({
      ...cred,
      id: base64UrlToBuffer(cred.id),
    })),
  };

  const credential = await navigator.credentials.create({ publicKey });
  return serializeCredential(credential);
}

export async function getBiometricAssertion(options) {
  const publicKey = {
    ...options,
    challenge: base64UrlToBuffer(options.challenge),
    allowCredentials: (options.allowCredentials || []).map((cred) => ({
      ...cred,
      id: base64UrlToBuffer(cred.id),
    })),
  };

  const credential = await navigator.credentials.get({ publicKey });
  return serializeCredential(credential);
}

function serializeCredential(credential) {
  const response = credential.response;
  const base = {
    id: credential.id,
    rawId: bufferToBase64Url(credential.rawId),
    type: credential.type,
  };

  if (response.attestationObject) {
    base.response = {
      clientDataJSON: bufferToBase64Url(response.clientDataJSON),
      attestationObject: bufferToBase64Url(response.attestationObject),
    };
  } else {
    base.response = {
      clientDataJSON: bufferToBase64Url(response.clientDataJSON),
      authenticatorData: bufferToBase64Url(response.authenticatorData),
      signature: bufferToBase64Url(response.signature),
      userHandle: response.userHandle ? bufferToBase64Url(response.userHandle) : null,
    };
  }

  return base;
}
