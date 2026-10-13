import { ActionIcon, Avatar, Badge, Button, CopyButton, Group, Select, Stack, Text, TextInput, Tooltip } from '@mantine/core';
import { IconCheck, IconCopy, IconTrash } from '@tabler/icons-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { useChangeRole, useInvitations, useInvite, useMe, useMembers, useRemoveMember, useRevokeInvitation } from '../api/hooks';
import type { Role } from '../api/types';
import { useBand } from '../band/band';
import { when } from '../lib/format';
import { done, failed } from '../lib/notify';
import { ActionButtons } from './ActionButtons';

/** The invitation link for a token (the one place it's shown: pass it on). */
function inviteLink(token: string) {
  return `${window.location.origin}/invite/${token}`;
}

/**
 * Who manages the band: owners change roles and remove members; anyone leaves; a band keeps an owner
 * (the server says so when not).
 */
export function MembersList() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const band = useBand();
  const me = useMe();
  const members = useMembers();
  const changeRole = useChangeRole();
  const remove = useRemoveMember();
  const owner = band.role === 'OWNER';
  return (
    <Stack gap="sm">
      {(members.data ?? []).map((m) => {
        const self = m.email !== null && m.email === me.data?.username;
        return (
          <Group key={m.id} justify="space-between" wrap="nowrap">
            <Group gap="sm" wrap="nowrap" style={{ minWidth: 0 }}>
              <Avatar color="brand" radius="xl" size={32}>{m.name.substring(0, 2).toUpperCase()}</Avatar>
              <Stack gap={0} style={{ minWidth: 0 }}>
                <Text fw={600} truncate>{m.displayName ?? m.name}{self ? ` ${t('members.you')}` : ''}</Text>
                {m.email && <Text size="xs" c="dimmed" truncate>{m.email}</Text>}
              </Stack>
            </Group>
            <Group gap={6} wrap="nowrap">
              {owner ? (
                <Select w={130} size="xs" aria-label={t('members.role')} allowDeselect={false} value={m.role}
                  data={(['OWNER', 'MANAGER'] as Role[]).map((r) => ({ value: r, label: t(`band.role.${r}`) }))}
                  onChange={(r) => r && r !== m.role && changeRole.mutate({ userId: m.id, role: r as Role }, {
                    onSuccess: () => done(t('members.roleChanged')), onError: failed,
                  })} />
              ) : (
                <Badge variant="light" color={m.role === 'OWNER' ? 'brand' : 'gray'} styles={{ label: { textTransform: 'none' } }}>
                  {t(`band.role.${m.role}`)}
                </Badge>
              )}
              {owner && !self && (
                <ActionButtons size="xs" actions={[{
                  label: t('members.remove'), danger: true,
                  run: () => remove.mutate(m.id, { onSuccess: () => done(t('members.removed')), onError: failed }),
                  confirm: { title: t('members.removeTitle', { name: m.displayName ?? m.name }), text: t('members.removeText'), yes: t('members.remove') },
                }]} />
              )}
            </Group>
          </Group>
        );
      })}
      <Group justify="flex-end">
        <ActionButtons size="xs" actions={[{
          label: t('members.leave'), danger: true,
          run: () => {
            const mine = members.data?.find((m) => m.email === me.data?.username);
            if (mine) remove.mutate(mine.id, { onSuccess: () => { done(t('members.left', { band: band.name })); navigate('/', { replace: true }); }, onError: failed });
          },
          confirm: { title: t('members.leaveTitle', { band: band.name }), text: t('members.leaveText'), yes: t('members.leave') },
        }]} />
      </Group>
    </Stack>
  );
}

/** Owners invite an e-mail address as a role and pass the link on; the open invitations, revocable. */
export function Invitations() {
  const { t } = useTranslation();
  const band = useBand();
  const invitations = useInvitations();
  const invite = useInvite();
  const revoke = useRevokeInvitation();
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<Role>('MANAGER');
  const [link, setLink] = useState<{ email: string; url: string } | null>(null);
  const owner = band.role === 'OWNER';
  return (
    <Stack gap="sm">
      {owner && (
        <form onSubmit={(e) => {
          e.preventDefault();
          invite.mutate({ email: email.trim(), role }, {
            onSuccess: (created) => { setLink({ email: created.invitation.email, url: inviteLink(created.token) }); setEmail(''); },
            onError: failed,
          });
        }}>
          <Group gap="xs" align="flex-end" wrap="wrap">
            <TextInput style={{ flex: '1 1 220px' }} type="email" label={t('members.inviteEmail')} required value={email}
              maxLength={320} onChange={(e) => setEmail(e.currentTarget.value)} />
            <Select w={140} label={t('members.role')} allowDeselect={false} value={role}
              data={(['MANAGER', 'OWNER'] as Role[]).map((r) => ({ value: r, label: t(`band.role.${r}`) }))}
              onChange={(r) => r && setRole(r as Role)} />
            <Button type="submit" loading={invite.isPending} disabled={!email.trim()}>{t('members.invite')}</Button>
          </Group>
        </form>
      )}
      {link && (
        <Stack gap={4}>
          <Text size="sm">{t('members.linkFor', { email: link.email })}</Text>
          <Group gap="xs" wrap="nowrap">
            <TextInput style={{ flex: 1 }} readOnly value={link.url} onFocus={(e) => e.currentTarget.select()} aria-label={t('members.link')} />
            <CopyButton value={link.url}>
              {({ copied, copy }) => (
                <Tooltip label={copied ? t('members.copied') : t('members.copy')}>
                  <ActionIcon variant="light" size="lg" onClick={copy} aria-label={t('members.copy')}>
                    {copied ? <IconCheck size={18} /> : <IconCopy size={18} />}
                  </ActionIcon>
                </Tooltip>
              )}
            </CopyButton>
          </Group>
          <Text size="xs" c="dimmed">{t('members.linkHelp')}</Text>
        </Stack>
      )}
      {(invitations.data ?? []).length > 0 && (
        <Stack gap={6}>
          <Text size="sm" fw={600}>{t('members.open')}</Text>
          {(invitations.data ?? []).map((i) => (
            <Group key={i.id} justify="space-between" wrap="nowrap">
              <Stack gap={0} style={{ minWidth: 0 }}>
                <Text size="sm" truncate>{i.email} · {t(`band.role.${i.role}`)}</Text>
                <Text size="xs" c="dimmed">{t('members.until', { when: when(i.expiresAt) })}</Text>
              </Stack>
              {owner && (
                <ActionIcon variant="subtle" color="red" aria-label={t('members.revoke')}
                  onClick={() => revoke.mutate(i.id, { onSuccess: () => done(t('members.revoked')), onError: failed })}>
                  <IconTrash size={18} />
                </ActionIcon>
              )}
            </Group>
          ))}
        </Stack>
      )}
    </Stack>
  );
}
