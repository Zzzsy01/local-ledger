import './style.css';
import { createReceiptService } from './application/receipt-service.js';
import { mountReceiptView, renderReceipt } from './ui/receipt-view.js';

const receipt = createReceiptService(renderReceipt);
mountReceiptView(receipt);
