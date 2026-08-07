import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { Check, Copy, Loader2 } from 'lucide-react';
import { createCharge, getOrderStatus } from '../api/public';
import { Money } from '../components/Money';
import { StateView } from '../components/StateView';
import { formatCountdown } from '../lib/format';
import { messageFor } from '../lib/errors';
import type { OrderChargeResponse } from '../types';
import styles from './PixPayment.module.css';

const POLL_INTERVAL_MS = 3000;

export function PixPayment() {
  const { orderId = '' } = useParams();
  const location = useLocation();
  const navigate = useNavigate();

  // Present on the normal path from checkout; absent after a refresh or a cold open.
  const initialCharge = (location.state as { charge?: OrderChargeResponse } | null)?.charge ?? null;

  const [charge, setCharge] = useState<OrderChargeResponse | null>(initialCharge);
  const [error, setError] = useState<string | null>(null);
  const [isExpired, setIsExpired] = useState(false);
  const [copied, setCopied] = useState(false);
  const [now, setNow] = useState(() => Date.now());
  // Bumped by the retry button to re-run the charge fetch below without calling
  // setState synchronously inside the effect body — see react-hooks/set-state-in-effect.
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    if (charge) {
      return;
    }
    // Idempotent server-side: returns the original charge, never a second payable one.
    createCharge(orderId)
      .then(setCharge)
      .catch((cause: unknown) => setError(messageFor(cause)));
  }, [charge, orderId, reloadToken]);

  const retryLoadCharge = () => {
    setError(null);
    setReloadToken((token) => token + 1);
  };

  // Polls until the order leaves PENDING. A failed poll is not a failed payment.
  useEffect(() => {
    if (!charge || isExpired) {
      return;
    }

    const controller = new AbortController();

    const check = async () => {
      try {
        const result = await getOrderStatus(orderId, controller.signal);
        if (result.status === 'PAID') {
          navigate(`/confirmacao/${orderId}`, { replace: true });
        } else if (result.status === 'EXPIRED' || result.status === 'CANCELED') {
          setIsExpired(true);
        }
      } catch {
        // Transient network or server blip. Keep polling.
      }
    };

    const interval = setInterval(check, POLL_INTERVAL_MS);
    return () => {
      clearInterval(interval);
      controller.abort();
    };
  }, [charge, isExpired, navigate, orderId]);

  // Ticks a clock rather than calling setState eagerly; the countdown itself is derived
  // below. Display only — expiry is whatever the API reports, never this timer.
  useEffect(() => {
    if (!charge) {
      return;
    }
    const interval = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(interval);
  }, [charge]);

  const msRemaining = charge ? new Date(charge.expiresAt).getTime() - now : 0;

  const copy = async () => {
    if (!charge) {
      return;
    }
    try {
      await navigator.clipboard.writeText(charge.payload);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      setError('Não foi possível copiar. Selecione o código manualmente.');
    }
  };

  if (isExpired) {
    return (
      <div className={styles.page}>
        <div className={styles.card}>
          <h1 className={styles.title}>Pedido expirado</h1>
          <p className={styles.hint}>
            O prazo para pagamento acabou. Seu carrinho foi mantido — é só refazer o pedido.
          </p>
          <Link to="/cardapio" className={styles.back}>
            Voltar ao cardápio
          </Link>
        </div>
      </div>
    );
  }

  if (error && !charge) {
    return <StateView kind="error" message={error} onRetry={retryLoadCharge} />;
  }

  if (!charge) {
    return <StateView kind="loading" message="gerando cobrança..." />;
  }

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <h1 className={styles.title}>Pague com PIX</h1>
        <p className={styles.hint}>Escaneie o QR Code no app do seu banco ou copie o código.</p>

        <img
          src={`data:image/png;base64,${charge.qrImageBase64}`}
          alt="QR Code do PIX"
          className={styles.qr}
        />

        <Money value={charge.total} size="lg" />

        <button type="button" className={styles.copy} onClick={copy}>
          {copied ? <Check size={18} /> : <Copy size={18} />}
          {copied ? 'Código copiado' : 'Copiar código PIX'}
        </button>

        {error ? (
          <p className={styles.hint} role="alert">
            {error}
          </p>
        ) : null}

        <div className={styles.waiting}>
          <Loader2 size={18} className={styles.spinner} />
          aguardando pagamento...
        </div>

        <p className={`${styles.countdown} ${msRemaining < 60_000 ? styles.expiring : ''}`}>
          expira em {formatCountdown(msRemaining)}
        </p>
      </div>
    </div>
  );
}
