import { Badge, Button, Group, PasswordInput, Stack, Switch, Text, TextInput } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useConnectPlatform, useDisconnectPlatform, usePlatformAccounts, usePlatforms, useSyncAction, useSyncStatus, useTestPlatform } from '../api/hooks';
import type { AccountField, Platform, PlatformAccount } from '../api/types';
import { useBand } from '../band/band';
import { clock, relative } from '../lib/format';
import { done, errorText, failed, failedWith } from '../lib/notify';
import { ActionButtons } from './ActionButtons';
import { Sheet } from './Sheet';

/** A field's name in the user's language — the page's own words for the common ones, else the platform's. */
function useFieldText() {
  const { t } = useTranslation();
  return {
    label: (f: AccountField) => t(`account.field.${f.name}`, { defaultValue: f.label }),
    help: (f: AccountField) => (f.help ? t(`account.help.${f.name}`, { defaultValue: f.help }) : undefined),
  };
}

/** The account's form, built from the platform's fields: a secret kept is never shown — typing one replaces it. */
function AccountSheet({ platform, account, opened, onClose }: {
  platform: Platform;
  account: PlatformAccount | undefined;
  opened: boolean;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const text = useFieldText();
  const connect = useConnectPlatform();
  const [settings, setSettings] = useState<Record<string, string>>(account?.settings ?? {});
  const [secrets, setSecrets] = useState<Record<string, string>>({});
  const kept = new Set(account?.secretsSet ?? []);
  const missing = platform.accountFields.some((f) => f.required
    && !(f.kind === 'SECRET' ? (secrets[f.name] ?? '').trim() || kept.has(f.name) : (settings[f.name] ?? '').trim()));
  return (
    <Sheet opened={opened} onClose={onClose} title={t('account.title', { platform: platform.name })}>
      <form onSubmit={(e) => {
        e.preventDefault();
        connect.mutate({ platform: platform.id, account: { settings, secrets } }, {
          onSuccess: () => { done(t('account.saved', { platform: platform.name })); onClose(); },
          onError: failed,
        });
      }}>
        <Stack gap="sm">
          {platform.accountFields.map((f) => f.kind === 'FLAG' ? (
            <Switch key={f.name} label={text.label(f)} description={text.help(f)}
              checked={settings[f.name] === 'true'}
              onChange={(e) => { const on = e.currentTarget.checked; setSettings((s) => ({ ...s, [f.name]: String(on) })); }} />
          ) : f.kind === 'SECRET' ? (
            <PasswordInput key={f.name} label={text.label(f)} description={text.help(f)} withAsterisk={f.required}
              autoComplete="new-password" placeholder={kept.has(f.name) ? t('account.kept') : undefined}
              value={secrets[f.name] ?? ''}
              onChange={(e) => { const v = e.currentTarget.value; setSecrets((s) => ({ ...s, [f.name]: v })); }} />
          ) : (
            <TextInput key={f.name} label={text.label(f)} description={text.help(f)} withAsterisk={f.required}
              autoComplete="off" value={settings[f.name] ?? ''}
              onChange={(e) => { const v = e.currentTarget.value; setSettings((s) => ({ ...s, [f.name]: v })); }} />
          ))}
          <Text size="xs" c="dimmed">{t('account.secretsHelp')}</Text>
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>{t('common.cancel')}</Button>
            <Button type="submit" disabled={missing} loading={connect.isPending}>{t('account.save')}</Button>
          </Group>
        </Stack>
      </form>
    </Sheet>
  );
}

/** One platform: whether the band connected it, how its last sign-in test went, and what can be done. */
function PlatformRow({ platform, account }: { platform: Platform; account: PlatformAccount | undefined }) {
  const { t } = useTranslation();
  const band = useBand();
  const owner = band.role === 'OWNER';
  const status = useSyncStatus();
  const sync = useSyncAction();
  const test = useTestPlatform();
  const disconnect = useDisconnectPlatform();
  const [editing, setEditing] = useState(false);
  const connected = account?.connected ?? false;
  const check = account?.lastCheck;
  const breaker = status.data?.breakers.find((b) => b.platform === platform.id && b.held && b.everyone)
    ?? status.data?.breakers.find((b) => b.platform === platform.id && b.heldUntil && !b.everyone);
  const login = account?.settings.login;
  const badge = !connected ? { color: 'gray', text: t('account.notConnected') }
    : breaker ? { color: 'orange', text: t('settings.heldBack') }
      : !check ? { color: 'blue', text: t('account.connected') }
        : check.ok ? { color: 'teal', text: t('account.signedIn') } : { color: 'red', text: t('account.signInFailed') };
  return (
    <Stack gap={6}>
      <Group justify="space-between" wrap="nowrap">
        <Stack gap={0} style={{ minWidth: 0 }}>
          <Text fw={700}>{platform.name}</Text>
          {connected && login && <Text size="sm" c="dimmed" truncate>{t('account.as', { login })}</Text>}
        </Stack>
        <Badge color={badge.color} variant="light" style={{ flexShrink: 0 }} styles={{ label: { textTransform: 'none' } }}>
          {badge.text}
        </Badge>
      </Group>
      {check && (
        <Text size="sm" c={check.ok ? 'dimmed' : 'red.8'}>
          {t(check.ok ? 'account.testedOk' : 'account.testedFailed', { when: relative(check.at), message: check.message ?? '' })}
        </Text>
      )}
      {breaker && (
        <Group justify="space-between" wrap="wrap" gap="xs">
          <Text size="sm" c="orange.9">
            {breaker.everyone ? t('inbox.heldEveryoneBody', { time: clock(breaker.heldUntil), last: breaker.lastFailure ?? '' })
              : t('inbox.heldBody', { failures: breaker.failures, time: clock(breaker.heldUntil), last: breaker.lastFailure ?? '' })}
          </Text>
          {!breaker.everyone && (
            <Button size="xs" onClick={() => sync.mutate({ kind: 'resumePlatform', platform: platform.id }, { onError: failed })}>{t('inbox.resume')}</Button>
          )}
        </Group>
      )}
      {test.isPending && <Text size="sm" c="dimmed">{t('account.testing')}</Text>}
      <ActionButtons size="xs" actions={[
        ...(owner ? [{ label: connected ? t('account.change') : t('account.connect'), primary: !connected, run: () => setEditing(true) }] : []),
        ...(connected ? [{
          label: t('account.test'),
          run: () => test.mutate(platform.id, {
            onSuccess: (a) => a.lastCheck?.ok ? done(t('account.testedOkToast', { platform: platform.name }))
              : failedWith(`${platform.name}: ${a.lastCheck?.message ?? ''}`),
            onError: failed,
          }),
        }] : []),
        ...(owner && connected ? [{
          label: t('account.disconnect'), danger: true,
          run: () => disconnect.mutate(platform.id, { onSuccess: () => done(t('account.disconnected', { platform: platform.name })), onError: failed }),
          confirm: { title: t('account.disconnectTitle', { platform: platform.name }), text: t('account.disconnectText', { platform: platform.name }), yes: t('account.disconnect') },
        }] : []),
      ]} />
      {!owner && !connected && <Text size="xs" c="dimmed">{t('account.ownersConnect')}</Text>}
      {editing && <AccountSheet platform={platform} account={account} opened onClose={() => setEditing(false)} />}
    </Stack>
  );
}

/** The band's platforms: each connected with the band's own account (Settings → This band). */
export function PlatformAccounts() {
  const { t } = useTranslation();
  const platforms = usePlatforms();
  const accounts = usePlatformAccounts();
  if (accounts.isError) return <Text size="sm" c="red.8">{errorText(accounts.error)}</Text>;
  return (
    <>
      {(platforms.data ?? []).filter((p) => p.accountFields.length > 0).map((p) => (
        <PlatformRow key={p.id} platform={p} account={accounts.data?.find((a) => a.platform === p.id)} />
      ))}
      <Text size="xs" c="dimmed">{t('account.intro')}</Text>
    </>
  );
}
