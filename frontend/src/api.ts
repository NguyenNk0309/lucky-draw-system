import type { Role } from './types';

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code?: string,
  ) {
    super(message);
  }
}

export interface DemoIdentity {
  userId: string;
  role: Role;
}

export async function api<T>(
  path: string,
  identity: DemoIdentity,
  init?: RequestInit,
): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      'X-Demo-User': identity.userId,
      'X-Demo-Role': identity.role,
      'X-Correlation-Id': crypto.randomUUID(),
      ...init?.headers,
    },
  });
  if (!response.ok) {
    const body = (await response.json().catch(() => ({}))) as {
      code?: string;
      message?: string;
    };
    throw new ApiError(
      body.message ?? `Request failed (${response.status})`,
      response.status,
      body.code,
    );
  }
  return (response.status === 204 ? undefined : await response.json()) as T;
}
