import type { Cart, Checkout, DisplayHome, Notification, Product, Shipment, Stock } from './types';

export const demoMemberId = 'mem_demo';

export const productImages: Record<string, string> = {
  'Everyday Cotton Tee':
    'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&w=900&q=80',
  'Ceramic Drip Set':
    'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=900&q=80',
  'Compact Travel Pouch':
    'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=900&q=80'
};

export const products: Product[] = [
  {
    id: 'prd_tee',
    name: 'Everyday Cotton Tee',
    brand: 'Namu',
    category: 'apparel',
    description: 'Soft tee for daily wear.',
    status: 'PUBLISHED',
    tags: ['daily', 'new'],
    skus: [
      {
        id: 'sku_tee_white_m',
        productId: 'prd_tee',
        name: 'White / M',
        price: { amount: 29000, currency: 'KRW' },
        attributes: { color: 'white', size: 'M' },
        status: 'ACTIVE'
      },
      {
        id: 'sku_tee_black_l',
        productId: 'prd_tee',
        name: 'Black / L',
        price: { amount: 29000, currency: 'KRW' },
        attributes: { color: 'black', size: 'L' },
        status: 'ACTIVE'
      }
    ]
  },
  {
    id: 'prd_drip',
    name: 'Ceramic Drip Set',
    brand: 'Slowbrew',
    category: 'home',
    description: 'Pour-over set with ceramic dripper and server.',
    status: 'PUBLISHED',
    tags: ['daily', 'premium'],
    skus: [
      {
        id: 'sku_drip_ivory',
        productId: 'prd_drip',
        name: 'Ivory',
        price: { amount: 87000, currency: 'KRW' },
        attributes: { color: 'ivory' },
        status: 'ACTIVE'
      },
      {
        id: 'sku_drip_moss',
        productId: 'prd_drip',
        name: 'Moss',
        price: { amount: 91000, currency: 'KRW' },
        attributes: { color: 'moss' },
        status: 'ACTIVE'
      }
    ]
  },
  {
    id: 'prd_pouch',
    name: 'Compact Travel Pouch',
    brand: 'Rove',
    category: 'travel',
    description: 'Water-resistant pouch with internal dividers.',
    status: 'PUBLISHED',
    tags: ['new'],
    skus: [
      {
        id: 'sku_pouch_sage',
        productId: 'prd_pouch',
        name: 'Sage',
        price: { amount: 34000, currency: 'KRW' },
        attributes: { color: 'sage' },
        status: 'ACTIVE'
      }
    ]
  }
];

export const displayHome: DisplayHome = {
  title: 'Impati Market',
  subtitle: 'Curated products with reliable checkout and delivery.',
  sections: [
    {
      key: 'daily_essentials',
      title: 'Daily Essentials',
      products: products
        .filter((product) => product.tags.includes('daily'))
        .map((product) => ({
          id: product.id,
          name: product.name,
          brand: product.brand,
          category: product.category,
          price: product.skus[0]?.price ?? null,
          tags: product.tags
        }))
    },
    {
      key: 'new_arrivals',
      title: 'New Arrivals',
      products: products
        .filter((product) => product.tags.includes('new'))
        .map((product) => ({
          id: product.id,
          name: product.name,
          brand: product.brand,
          category: product.category,
          price: product.skus[0]?.price ?? null,
          tags: product.tags
        }))
    },
    {
      key: 'premium_picks',
      title: 'Premium Picks',
      products: products
        .filter((product) => product.tags.includes('premium'))
        .map((product) => ({
          id: product.id,
          name: product.name,
          brand: product.brand,
          category: product.category,
          price: product.skus[0]?.price ?? null,
          tags: product.tags
        }))
    }
  ]
};

export const initialCart: Cart = {
  memberId: demoMemberId,
  lines: [],
  version: 0
};

export const initialStock: Stock[] = [
  { skuId: 'sku_tee_white_m', onHand: 20, reserved: 0, available: 20 },
  { skuId: 'sku_tee_black_l', onHand: 20, reserved: 0, available: 20 },
  { skuId: 'sku_drip_ivory', onHand: 20, reserved: 0, available: 20 },
  { skuId: 'sku_drip_moss', onHand: 20, reserved: 0, available: 20 },
  { skuId: 'sku_pouch_sage', onHand: 20, reserved: 0, available: 20 }
];

export const initialNotifications: Notification[] = [];

export function createDemoCheckout(cart: Cart): Checkout {
  const now = Date.now();
  const orderId = `ord_demo_${now}`;
  const paymentId = `pay_demo_${now}`;
  const shipmentId = `shp_demo_${now}`;
  const reservationId = `rsv_demo_${now}`;
  const transactionId = `txn_demo_${now}`;
  const trackingNumber = `trk_demo_${now}`;
  const lines = cart.lines.map((line) => {
    const product = products.find((candidate) => candidate.skus.some((sku) => sku.id === line.skuId));
    const sku = product?.skus.find((candidate) => candidate.id === line.skuId);
    if (!product || !sku) {
      throw new Error('unknown sku');
    }
    return {
      skuId: sku.id,
      productId: product.id,
      productName: product.name,
      skuName: sku.name,
      quantity: line.quantity,
      unitPrice: sku.price,
      lineTotal: { amount: sku.price.amount * line.quantity, currency: sku.price.currency }
    };
  });
  const total = lines.reduce((sum, line) => sum + line.lineTotal.amount, 0);
  return {
    order: {
      id: orderId,
      memberId: demoMemberId,
      status: 'FULFILLING',
      lines,
      total: { amount: total, currency: 'KRW' },
      priceBreakdown: {
        productAmount: { amount: total, currency: 'KRW' },
        shippingFee: { amount: 0, currency: 'KRW' },
        totalAmount: { amount: total, currency: 'KRW' }
      },
      shippingAddress: {
        id: 'addr_demo',
        alias: 'home',
        recipient: 'Demo Customer',
        phone: '010-0000-0000',
        line1: '123 Commerce Road',
        city: 'Seoul',
        postalCode: '04524',
        defaultAddress: true
      },
      paymentId,
      shipmentId,
      inventoryReservationId: reservationId,
      checkoutStatus: 'SUCCEEDED',
      paymentCleanupStatus: 'NONE',
      failureCode: null
    },
    payment: {
      id: paymentId,
      orderId,
      memberId: demoMemberId,
      amount: { amount: total, currency: 'KRW' },
      method: 'CARD',
      status: 'CAPTURED',
      transactionId
    },
    shipment: {
      id: shipmentId,
      orderId,
      memberId: demoMemberId,
      address: {
        id: 'addr_demo',
        alias: 'home',
        recipient: 'Demo Customer',
        phone: '010-0000-0000',
        line1: '123 Commerce Road',
        city: 'Seoul',
        postalCode: '04524',
        defaultAddress: true
      },
      status: 'READY',
      trackingNumber
    }
  };
}

export function deliverDemoShipment(shipment: Shipment): Shipment {
  return { ...shipment, status: 'DELIVERED' };
}
