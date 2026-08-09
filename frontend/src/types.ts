export type Role = 'CUSTOMER' | 'SELLER';
export interface Order {
  id: string;
  userId: string;
  total: number;
  createdAt: string;
}
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
  rewardWinners: number;
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
  pendingRewards: number;
  releasedRewards: number;
  rewardStatus?: 'PENDING' | 'DELIVERING';
  reward?: { type: 'PRODUCT' | 'COUPON'; reference: string };
  lastUpdatedAt?: string;
}
export interface LuckyEntry {
  id: string;
  wheelSegment: number;
  rewardPending: boolean;
}
export interface Notification {
  id: string;
  campaignId: string;
  winnerEntryId: string;
  message: string;
  sentAt: string;
}
export interface RewardClaim {
  id: string;
  campaignId: string;
  rewardType: 'PRODUCT' | 'COUPON';
  reference: string;
  deliveryReference?: string;
  deliveredAt?: string;
}
