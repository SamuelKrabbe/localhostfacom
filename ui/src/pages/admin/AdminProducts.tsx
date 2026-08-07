import { useEffect, useState } from 'react';
import type { ChangeEvent, FormEvent } from 'react';
import {
  createProduct,
  deleteProduct,
  listProducts,
  updateProduct,
  uploadImage,
} from '../../api/admin';
import { StateView } from '../../components/StateView';
import { messageFor } from '../../lib/errors';
import { formatCurrency } from '../../lib/format';
import type { Product } from '../../types';
import admin from './admin.module.css';
import styles from './AdminProducts.module.css';

interface FormState {
  id: string | null;
  name: string;
  price: string;
  imageId: string | null;
  imageUrl: string | null;
  active: boolean;
}

const EMPTY_FORM: FormState = {
  id: null,
  name: '',
  price: '',
  imageId: null,
  imageUrl: null,
  active: true,
};

export function AdminProducts() {
  const [products, setProducts] = useState<Product[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [formError, setFormError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [isUploading, setIsUploading] = useState(false);

  useEffect(() => {
    listProducts()
      .then(setProducts)
      .catch((error: unknown) => setLoadError(messageFor(error)));
  }, [reloadToken]);

  const reload = () => {
    setLoadError(null);
    setProducts(null);
    setReloadToken((token) => token + 1);
  };

  const edit = (product: Product) => {
    setFormError(null);
    setForm({
      id: product.id,
      name: product.name,
      price: String(product.price),
      imageId: product.imageId,
      imageUrl: product.imageUrl,
      active: product.active,
    });
  };

  const pickImage = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setIsUploading(true);
    setFormError(null);
    try {
      const image = await uploadImage(file);
      setForm((current) => ({ ...current, imageId: image.id, imageUrl: image.url }));
    } catch (error: unknown) {
      setFormError(messageFor(error));
    } finally {
      setIsUploading(false);
      // Lets the same file be picked again after a failed upload.
      event.target.value = '';
    }
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setIsSaving(true);
    setFormError(null);
    try {
      const input = {
        name: form.name,
        price: Number(form.price.replace(',', '.')),
        imageId: form.imageId,
        active: form.active,
      };
      if (form.id) {
        await updateProduct(form.id, input);
      } else {
        await createProduct(input);
      }
      setForm(EMPTY_FORM);
      reload();
    } catch (error: unknown) {
      setFormError(messageFor(error));
    } finally {
      setIsSaving(false);
    }
  };

  const remove = async (product: Product) => {
    if (!window.confirm(`Tirar "${product.name}" do cardápio?`)) {
      return;
    }
    try {
      await deleteProduct(product.id);
      if (form.id === product.id) {
        setForm(EMPTY_FORM);
      }
      reload();
    } catch (error: unknown) {
      setFormError(messageFor(error));
    }
  };

  return (
    <div className={admin.page}>
      <div className={admin.head}>
        <div>
          <h1 className={admin.title}>Produtos</h1>
          <p className={admin.subtitle}>
            O que está à venda no cardápio. Produtos já vendidos são desativados, nunca
            apagados.
          </p>
        </div>
      </div>

      <section className={admin.panel}>
        <h2 className={admin.panelTitle}>{form.id ? 'Editar produto' : 'Novo produto'}</h2>
        <form className={admin.form} onSubmit={submit}>
          <div className={admin.fields}>
            <div className={admin.field}>
              <label className={admin.label} htmlFor="name">
                Nome
              </label>
              <input
                id="name"
                className={admin.input}
                maxLength={120}
                required
                value={form.name}
                onChange={(event) => setForm({ ...form, name: event.target.value })}
              />
            </div>

            <div className={admin.field}>
              <label className={admin.label} htmlFor="price">
                Preço (R$)
              </label>
              <input
                id="price"
                className={`${admin.input} ${admin.numeric}`}
                type="number"
                step="0.01"
                min="0.01"
                required
                value={form.price}
                onChange={(event) => setForm({ ...form, price: event.target.value })}
              />
            </div>

            <div className={admin.field}>
              <label className={admin.label} htmlFor="image">
                Imagem
              </label>
              <input
                id="image"
                className={admin.input}
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={pickImage}
              />
            </div>
          </div>

          {isUploading ? <p className={admin.subtitle}>Enviando imagem...</p> : null}

          {form.imageUrl ? (
            <div className={styles.preview}>
              <img className={styles.previewImage} src={form.imageUrl} alt="" />
              <button
                type="button"
                className={admin.button}
                onClick={() => setForm({ ...form, imageId: null, imageUrl: null })}
              >
                Remover imagem
              </button>
            </div>
          ) : null}

          {form.id ? (
            <label className={styles.checkbox}>
              <input
                type="checkbox"
                checked={form.active}
                onChange={(event) => setForm({ ...form, active: event.target.checked })}
              />
              Ativo no cardápio
            </label>
          ) : null}

          {formError ? (
            <p className={admin.error} role="alert">
              {formError}
            </p>
          ) : null}

          <div className={admin.actions}>
            <button
              type="submit"
              className={`${admin.button} ${admin.primary}`}
              disabled={isSaving || isUploading}
            >
              {form.id ? 'Salvar' : 'Adicionar'}
            </button>
            {form.id ? (
              <button
                type="button"
                className={admin.button}
                onClick={() => {
                  setForm(EMPTY_FORM);
                  setFormError(null);
                }}
              >
                Cancelar
              </button>
            ) : null}
          </div>
        </form>
      </section>

      <section className={admin.panel}>
        <h2 className={admin.panelTitle}>Cardápio</h2>
        {loadError ? (
          <StateView kind="error" message={loadError} onRetry={reload} />
        ) : !products ? (
          <StateView kind="loading" message="carregando produtos..." />
        ) : products.length === 0 ? (
          <StateView kind="empty" message="Nenhum produto cadastrado ainda." />
        ) : (
          <ul className={admin.list}>
            {products.map((product) => (
              <li key={product.id} className={admin.row}>
                {product.imageUrl ? (
                  <img className={styles.thumb} src={product.imageUrl} alt="" />
                ) : (
                  <div className={`${styles.thumb} ${styles.thumbEmpty}`} />
                )}
                <div className={admin.rowInfo}>
                  <p className={admin.rowTitle}>{product.name}</p>
                  <p className={admin.rowMeta}>{formatCurrency(product.price)}</p>
                </div>
                {product.active ? null : <span className={admin.badge}>inativo</span>}
                <div className={admin.actions}>
                  <button type="button" className={admin.button} onClick={() => edit(product)}>
                    Editar
                  </button>
                  <button
                    type="button"
                    className={`${admin.button} ${admin.danger}`}
                    onClick={() => remove(product)}
                  >
                    Remover
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
