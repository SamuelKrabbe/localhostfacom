import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import {
  createExpense,
  deleteExpense,
  getSettings,
  listExpenses,
  updateSettings,
} from '../../api/admin';
import { Money } from '../../components/Money';
import { StateView } from '../../components/StateView';
import { messageFor } from '../../lib/errors';
import { formatIsoDate } from '../../lib/format';
import type { Expense, Settings } from '../../types';
import admin from './admin.module.css';

export function AdminExpenses() {
  const [expenses, setExpenses] = useState<Expense[] | null>(null);
  const [settings, setSettings] = useState<Settings | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  const [goalTarget, setGoalTarget] = useState('');
  const [crowdfundingUrl, setCrowdfundingUrl] = useState('');
  const [settingsError, setSettingsError] = useState<string | null>(null);
  const [isSavingSettings, setIsSavingSettings] = useState(false);
  const [settingsSaved, setSettingsSaved] = useState(false);

  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [incurredOn, setIncurredOn] = useState('');
  const [expenseError, setExpenseError] = useState<string | null>(null);
  const [isSavingExpense, setIsSavingExpense] = useState(false);

  useEffect(() => {
    Promise.all([listExpenses(), getSettings()])
      .then(([loadedExpenses, loadedSettings]) => {
        setExpenses(loadedExpenses);
        setSettings(loadedSettings);
        setGoalTarget(String(loadedSettings.goalTarget));
        setCrowdfundingUrl(loadedSettings.crowdfundingUrl ?? '');
      })
      .catch((error: unknown) => setLoadError(messageFor(error)));
  }, [reloadToken]);

  const reload = () => {
    setLoadError(null);
    setExpenses(null);
    setSettings(null);
    setReloadToken((token) => token + 1);
  };

  const saveSettings = async (event: FormEvent) => {
    event.preventDefault();
    setIsSavingSettings(true);
    setSettingsError(null);
    setSettingsSaved(false);
    try {
      const trimmedUrl = crowdfundingUrl.trim();
      const saved = await updateSettings({
        goalTarget: Number(goalTarget.replace(',', '.')),
        // The API validates the URL against ^https?://.+ and treats null as "no link";
        // an empty string would fail that pattern.
        crowdfundingUrl: trimmedUrl === '' ? null : trimmedUrl,
      });
      setSettings(saved);
      setSettingsSaved(true);
    } catch (error: unknown) {
      setSettingsError(messageFor(error));
    } finally {
      setIsSavingSettings(false);
    }
  };

  const addExpense = async (event: FormEvent) => {
    event.preventDefault();
    setIsSavingExpense(true);
    setExpenseError(null);
    try {
      const created = await createExpense({
        description,
        amount: Number(amount.replace(',', '.')),
        incurredOn: incurredOn === '' ? undefined : incurredOn,
      });
      setExpenses((current) => [created, ...(current ?? [])]);
      setDescription('');
      setAmount('');
      setIncurredOn('');
    } catch (error: unknown) {
      setExpenseError(messageFor(error));
    } finally {
      setIsSavingExpense(false);
    }
  };

  const remove = async (expense: Expense) => {
    if (!window.confirm(`Apagar a despesa "${expense.description}"?`)) {
      return;
    }
    try {
      await deleteExpense(expense.id);
      setExpenses((current) => (current ?? []).filter((item) => item.id !== expense.id));
    } catch (error: unknown) {
      setExpenseError(messageFor(error));
    }
  };

  if (loadError) {
    return <StateView kind="error" message={loadError} onRetry={reload} />;
  }

  if (!expenses || !settings) {
    return <StateView kind="loading" message="carregando despesas..." />;
  }

  const total = expenses.reduce((sum, expense) => sum + expense.amount, 0);

  return (
    <div className={admin.page}>
      <div className={admin.head}>
        <div>
          <h1 className={admin.title}>Despesas &amp; meta</h1>
          <p className={admin.subtitle}>
            Tudo aqui aparece no portal público: a meta, o link da vaquinha e cada despesa.
          </p>
        </div>
      </div>

      <section className={admin.panel}>
        <h2 className={admin.panelTitle}>Meta de arrecadação</h2>
        <form className={admin.form} onSubmit={saveSettings}>
          <div className={admin.fields}>
            <div className={admin.field}>
              <label className={admin.label} htmlFor="goal">
                Meta (R$)
              </label>
              <input
                id="goal"
                className={`${admin.input} ${admin.numeric}`}
                type="number"
                step="0.01"
                min="0.01"
                required
                value={goalTarget}
                onChange={(event) => setGoalTarget(event.target.value)}
              />
            </div>
            <div className={admin.field}>
              <label className={admin.label} htmlFor="crowdfunding">
                Link da vaquinha
              </label>
              <input
                id="crowdfunding"
                className={admin.input}
                type="url"
                placeholder="https://..."
                maxLength={1024}
                value={crowdfundingUrl}
                onChange={(event) => setCrowdfundingUrl(event.target.value)}
              />
            </div>
          </div>

          {settingsError ? (
            <p className={admin.error} role="alert">
              {settingsError}
            </p>
          ) : null}

          <div className={admin.actions}>
            <button
              type="submit"
              className={`${admin.button} ${admin.primary}`}
              disabled={isSavingSettings}
            >
              Salvar meta
            </button>
            {settingsSaved ? <p className={admin.subtitle}>Meta atualizada.</p> : null}
          </div>
        </form>
      </section>

      <section className={admin.panel}>
        <h2 className={admin.panelTitle}>Nova despesa</h2>
        <form className={admin.form} onSubmit={addExpense}>
          <div className={admin.fields}>
            <div className={admin.field}>
              <label className={admin.label} htmlFor="description">
                Descrição
              </label>
              <input
                id="description"
                className={admin.input}
                maxLength={255}
                required
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            </div>
            <div className={admin.field}>
              <label className={admin.label} htmlFor="amount">
                Valor (R$)
              </label>
              <input
                id="amount"
                className={`${admin.input} ${admin.numeric}`}
                type="number"
                step="0.01"
                min="0.01"
                required
                value={amount}
                onChange={(event) => setAmount(event.target.value)}
              />
            </div>
            <div className={admin.field}>
              <label className={admin.label} htmlFor="incurredOn">
                Data
              </label>
              <input
                id="incurredOn"
                className={admin.input}
                type="date"
                value={incurredOn}
                onChange={(event) => setIncurredOn(event.target.value)}
              />
            </div>
          </div>

          {expenseError ? (
            <p className={admin.error} role="alert">
              {expenseError}
            </p>
          ) : null}

          <div className={admin.actions}>
            <button
              type="submit"
              className={`${admin.button} ${admin.primary}`}
              disabled={isSavingExpense}
            >
              Registrar despesa
            </button>
          </div>
        </form>
      </section>

      <section className={admin.panel}>
        <div className={admin.head}>
          <h2 className={admin.panelTitle}>Despesas</h2>
          <Money value={total} size="sm" tone="muted" />
        </div>
        {expenses.length === 0 ? (
          <StateView kind="empty" message="Nenhuma despesa registrada." />
        ) : (
          <ul className={admin.list}>
            {expenses.map((expense) => (
              <li key={expense.id} className={admin.row}>
                <div className={admin.rowInfo}>
                  <p className={admin.rowTitle}>{expense.description}</p>
                  <p className={admin.rowMeta}>{formatIsoDate(expense.incurredOn)}</p>
                </div>
                <Money value={expense.amount} size="sm" />
                <button
                  type="button"
                  className={`${admin.button} ${admin.danger}`}
                  onClick={() => remove(expense)}
                >
                  Apagar
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
