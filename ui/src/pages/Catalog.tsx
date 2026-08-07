import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { createOrder, listProducts } from '../api/public';
import { CartBar } from '../components/CartBar';
import { ProductCard } from '../components/ProductCard';
import { StateView } from '../components/StateView';
import { saveOrderSnapshot } from '../cart/storage';
import { useCart } from '../cart/useCart';
import { messageFor } from '../lib/errors';
import type { Product } from '../types';
import styles from './Catalog.module.css';

export function Catalog() {
  const [products, setProducts] = useState<Product[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  // Bumped by retry() to re-run the effect below without calling setState synchronously
  // inside the effect body itself — that pattern trips react-hooks/set-state-in-effect.
  const [reloadToken, setReloadToken] = useState(0);

  const cart = useCart();
  const navigate = useNavigate();

  useEffect(() => {
    listProducts()
      .then(setProducts)
      .catch((error: unknown) => setLoadError(messageFor(error)));
  }, [reloadToken]);

  const retry = () => {
    setLoadError(null);
    setProducts(null);
    setReloadToken((token) => token + 1);
  };

  const checkout = async () => {
    setIsSubmitting(true);
    setCheckoutError(null);
    try {
      const items = cart.items.map((item) => ({ productId: item.id, quantity: item.quantity }));
      const charge = await createOrder(items);
      // Written before navigating: the receipt screen has no other source for line items.
      saveOrderSnapshot(charge.orderId, cart.items);
      navigate(`/pagamento/${charge.orderId}`, { state: { charge } });
    } catch (error: unknown) {
      setCheckoutError(messageFor(error));
    } finally {
      setIsSubmitting(false);
    }
  };

  if (loadError) {
    return <StateView kind="error" message={loadError} onRetry={retry} />;
  }

  if (!products) {
    return <StateView kind="loading" message="carregando cardápio..." />;
  }

  if (products.length === 0) {
    return <StateView kind="empty" message="Nenhum produto disponível no momento." />;
  }

  return (
    <>
      <div className={styles.page}>
        <div className={styles.intro}>
          <h1 className={styles.title}>Cardápio</h1>
          <p className={styles.subtitle}>
            Monte seu pedido e pague com PIX. Sem cadastro, sem login.
          </p>
        </div>

        <ul className={styles.list}>
          {products.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              quantity={cart.quantityOf(product.id)}
              onChange={cart.setQuantity}
            />
          ))}
        </ul>
      </div>

      <CartBar
        totalItems={cart.totalItems}
        totalPrice={cart.totalPrice}
        isSubmitting={isSubmitting}
        error={checkoutError}
        onCheckout={checkout}
      />
    </>
  );
}
