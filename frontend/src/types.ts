export type Role = 'CUSTOMER' | 'SELLER';

export interface Campaign {
  id: string;
  sellerId: string;
  name: string;
  status: 'DRAFT' | 'ACTIVE' | 'ENDED' | 'DRAWN' | 'CANCELLED';
  maxEntriesPerUser: number;
  startAt: string;
  endAt: string;
  reward: { type: 'PRODUCT' | 'COUPON'; reference: string };
  winnerEntryId?: string;
  snapshotHash?: string;
}

export interface Ticket {
  id: string;
  orderId: string;
  status: 'ISSUED' | 'CONSUMED';
  campaignId?: string;
}

export interface Stats {
  campaignId: string;
  name?: string;
  status?: string;
  totalEntries: number;
  distinctParticipants: number;
  winnerEntryId?: string;
  winnerUserId?: string;
  snapshotHash?: string;
  lastUpdatedAt?: string;
}

export interface MyResult {
  campaignId: string;
  entryIds: string[];
  remainingQuota: number;
  winnerEntryId?: string;
  won: boolean;
  lastUpdatedAt?: string;
}
