import { Link, Route, Routes } from 'react-router-dom';
import { App } from './App';
import { OrdersPage } from './OrdersPage';

export function CommerceRoutes() {
  return <Routes>
    <Route path="/" element={<App />} />
    <Route path="/orders" element={<OrdersPage />} />
    <Route path="/orders/:orderId" element={<OrdersPage />} />
    <Route path="*" element={<main className="orders-layout"><h1>페이지를 찾을 수 없습니다</h1><Link to="/">쇼핑 화면으로</Link></main>} />
  </Routes>;
}
