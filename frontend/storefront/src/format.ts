import type { Money } from './types';

export function formatMoney(money?: Money | null): string {
  if (!money) {
    return '-';
  }
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency: money.currency,
    maximumFractionDigits: 0
  }).format(money.amount);
}

export function compactStatus(status: string): string {
  return status.replaceAll('_', ' ').toLowerCase();
}

