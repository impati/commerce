export type Money = {
  amount: number;
  currency: string;
};

export type Sku = {
  id: string;
  productId: string;
  name: string;
  price: Money;
  attributes: Record<string, string>;
  status: string;
};

export type Product = {
  id: string;
  name: string;
  brand: string;
  category: string;
  description: string;
  status: string;
  tags: string[];
  skus: Sku[];
};

export type ProductCard = {
  id: string;
  name: string;
  brand: string;
  category: string;
  price: Money | null;
  tags: string[];
};

export type DisplaySection = {
  key: string;
  title: string;
  products: ProductCard[];
};

export type DisplayHome = {
  title: string;
  subtitle: string;
  sections: DisplaySection[];
};

export type CartLine = {
  skuId: string;
  quantity: number;
  productName?: string | null;
  skuName?: string | null;
  availableQuantity?: number | null;
  informationAvailable?: boolean;
};

export type PurchaseQuoteLine = {
  skuId: string;
  quantity: number;
  unitPrice: Money;
  lineTotal: Money;
};

export type PurchaseQuote = {
  id: string;
  cartVersion: number;
  lines: PurchaseQuoteLine[];
  total: Money;
};

export type Cart = {
  memberId: string;
  lines: CartLine[];
  version: number;
  quote?: PurchaseQuote | null;
  unavailable?: string[];
  checkoutAllowed?: boolean;
};

export type Address = {
  id: string;
  alias: string;
  recipient: string;
  phone: string;
  line1: string;
  city: string;
  postalCode: string;
  defaultAddress: boolean;
};

export type OrderLine = {
  skuId: string;
  productId: string;
  productName: string;
  skuName: string;
  quantity: number;
  unitPrice: Money;
  lineTotal: Money;
};

export type Order = {
  id: string;
  memberId: string;
  status: string;
  lines: OrderLine[];
  total: Money;
  shippingAddress: Address;
  paymentId: string | null;
  shipmentId: string | null;
  inventoryReservationId: string | null;
  checkoutStatus: 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | null;
  paymentCleanupStatus: 'NONE' | 'CHECKING' | 'CANCELLING' | 'REFUNDING' | 'DONE' | null;
  failureCode: string | null;
};

export type Payment = {
  id: string;
  orderId: string;
  memberId: string;
  amount: Money;
  method: string;
  status: string;
  transactionId: string;
};

export type Shipment = {
  id: string;
  orderId: string;
  memberId: string;
  address: Address;
  status: string;
  trackingNumber: string;
};

export type Checkout = {
  order: Order;
  payment: Payment | null;
  shipment: Shipment | null;
};

export type Stock = {
  skuId: string;
  onHand: number;
  reserved: number;
  available: number;
};

export type Notification = {
  id: string;
  eventType: string;
  memberId: string;
  subject: string;
  body: string;
};


export type Member = {
  id: string;
  email: string;
  name: string;
  status: string;
  addresses: {
    id: string;
    alias: string;
    recipient: string;
    phone: string;
    line1: string;
    city: string;
    postalCode: string;
    defaultAddress: boolean;
  }[];
};

export type IssuedAccessToken = {
  accessToken: string;
  accessTokenExpiresAt: string;
};

export type OrderCustomerState = 'PROCESSING' | 'CHECKING' | 'SUCCEEDED' | 'FAILED';

export type OrderSummary = {
  id: string;
  orderedAt: string;
  representativeProductName: string;
  representativeSkuName: string;
  additionalProductCount: number;
  totalQuantity: number;
  total: Money;
  checkoutResult: OrderCustomerState;
  orderStatus: string | null;
};

export type OrderPage = { items: OrderSummary[]; nextCursor: string | null };

export type OrderDetail = {
  id: string;
  orderedAt: string;
  checkoutResult: OrderCustomerState;
  orderStatus: string | null;
  lines: OrderLine[];
  total: Money;
  shippingAddress: Pick<Address, 'recipient' | 'phone' | 'line1' | 'city' | 'postalCode'>;
  trackingNumber: string | null;
  timeline: { type: string; occurredAt: string }[];
};
