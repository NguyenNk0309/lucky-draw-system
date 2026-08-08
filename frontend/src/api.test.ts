import { afterEach, describe, expect, it, vi } from 'vitest';
import { api } from './api';

describe('api', () => {
  afterEach(() => vi.restoreAllMocks());

  it('preserves the distinct entry conflict code and message', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            code: 'ENTRY_QUOTA_REACHED',
            message: 'Entry quota reached',
          }),
          { status: 409, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );

    await expect(
      api('/entries', { userId: 'customer-1', role: 'CUSTOMER' }),
    ).rejects.toEqual(
      expect.objectContaining({
        status: 409,
        code: 'ENTRY_QUOTA_REACHED',
        message: 'Entry quota reached',
      }),
    );
  });
});
