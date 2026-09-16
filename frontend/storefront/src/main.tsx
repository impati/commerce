import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { CommerceRoutes } from './CommerceRoutes';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter>
      <CommerceRoutes />
    </BrowserRouter>
  </React.StrictMode>
);
