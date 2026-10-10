import { Avatar, Button, Group, Paper, SegmentedControl, Stack, Switch, Text, Title, useMantineColorScheme } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { useDeleteMyAccount, useLogout, useMe, useSyncAction, useSyncStatus, useUpdateBand } from '../api/hooks';
import { ActionButtons } from '../components/ActionButtons';
import { bandAddress, useBand } from '../band/band';
import { AuditLog, BandDataActions } from '../components/BandData';
import { BandFields, useBandFields } from '../components/BandFields';
import { Invitations, MembersList } from '../components/BandMembers';
import { CalendarConnection } from '../components/CalendarSetup';
import { PageHeader } from '../components/PageHeader';
import { PlatformAccounts } from '../components/PlatformAccounts';
import { LANGUAGES, setLanguage } from '../i18n';
import { setTimeFormat, timeFormat, type TimeFormat } from '../lib/format';
import { done, failed } from '../lib/notify';

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Paper withBorder p="md">
      <Title order={2} fz={13} c="dimmed" tt="uppercase" mb="sm" style={{ letterSpacing: '0.06em' }}>{title}</Title>
      <Stack gap="sm">{children}</Stack>
    </Paper>
  );
}

function Heading({ children }: { children: React.ReactNode }) {
  return <Title order={2} fz={18} mt="sm">{children}</Title>;
}

/** The band's name and address — owners change them; the page then moves to the new address. */
function BandSection() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const band = useBand();
  const update = useUpdateBand();
  const fields = useBandFields({ name: band.name, slug: band.slug });
  const owner = band.role === 'OWNER';
  const unchanged = fields.value.name === band.name && fields.value.slug === band.slug;
  return (
    <Section title={t('settings.band')}>
      {owner ? (
        <form onSubmit={(e) => {
          e.preventDefault();
          update.mutate(fields.value, {
            onSuccess: (changed) => {
              done(t('band.saved'));
              navigate(bandAddress(changed.slug, '/settings'), { replace: true });
            },
            onError: failed,
          });
        }}>
          <Stack gap="sm">
            <BandFields fields={fields} />
            <Group justify="flex-end">
              <Button type="submit" disabled={!fields.valid || unchanged} loading={update.isPending}>{t('band.save')}</Button>
            </Group>
            <Switch
              label={t('band.publicList')}
              description={t('band.publicListHint', { address: `${window.location.origin}/gigs/${band.slug}` })}
              checked={band.publicList}
              disabled={update.isPending}
              onChange={(e) => update.mutate(
                { name: band.name, slug: band.slug, publicList: e.currentTarget.checked },
                { onSuccess: () => done(t('band.saved')), onError: failed },
              )}
            />
          </Stack>
        </form>
      ) : (
        <>
          <Text fw={700}>{band.name}</Text>
          <Text size="sm" c="dimmed">{t('band.ownersChange')}</Text>
        </>
      )}
    </Section>
  );
}

/** Who manages the band, and bringing people in. */
function MembersSection() {
  const { t } = useTranslation();
  return (
    <Section title={t('settings.members')}>
      <MembersList />
      <Invitations />
    </Section>
  );
}

export function SettingsPage() {
  const { t, i18n } = useTranslation();
  const { colorScheme, setColorScheme } = useMantineColorScheme();
  const me = useMe();
  const logout = useLogout();
  const deleteAccount = useDeleteMyAccount();
  const status = useSyncStatus();
  const sync = useSyncAction();
  const paused = status.data?.paused ?? false;
  const band = useBand();
  const [time, setTime] = useState(timeFormat());
  return (
    <Stack gap="md" maw={720}>
      <PageHeader title={t('settings.title')} phoneBack="/more" />
      <Heading>{t('settings.you')}</Heading>
      <Section title={t('settings.account')}>
        <Group justify="space-between" wrap="wrap">
          <Group gap="sm">
            <Avatar color="brand" radius="xl">{(me.data?.name ?? '?').substring(0, 2).toUpperCase()}</Avatar>
            <Text fw={600}>{t('settings.signedInAs', { name: me.data?.username })}</Text>
          </Group>
          <Button variant="default" loading={logout.isPending} onClick={() => logout.mutate()}>{t('settings.signOut')}</Button>
        </Group>
        <Group justify="flex-end">
          <ActionButtons size="xs" actions={[{
            label: t('settings.deleteAccount'), danger: true,
            run: () => deleteAccount.mutate(undefined, { onSuccess: () => logout.mutate(), onError: failed }),
            confirm: { title: t('settings.deleteAccountTitle'), text: t('settings.deleteAccountText'), yes: t('settings.deleteAccount') },
          }]} />
        </Group>
      </Section>
      <Section title={t('settings.language')}>
        <SegmentedControl fullWidth value={i18n.language} onChange={setLanguage} data={LANGUAGES.map((l) => ({ value: l.code, label: l.name }))} />
      </Section>
      <Section title={t('settings.timeFormat')}>
        <SegmentedControl fullWidth value={time}
          onChange={(v) => { setTimeFormat(v as TimeFormat); setTime(v as TimeFormat); }}
          data={[{ value: '24h', label: t('settings.time24') }, { value: '12h', label: t('settings.time12') }]} />
      </Section>
      <Section title={t('settings.appearance')}>
        <SegmentedControl fullWidth value={colorScheme} onChange={(v) => setColorScheme(v as 'light' | 'dark' | 'auto')}
          data={[{ value: 'auto', label: t('settings.auto') }, { value: 'light', label: t('settings.light') }, { value: 'dark', label: t('settings.dark') }]} />
      </Section>
      <Heading>{t('settings.thisBand')}</Heading>
      <BandSection />
      <MembersSection />
      <Section title={t('settings.platforms')}>
        <PlatformAccounts />
      </Section>
      <Section title={t('settings.calendar')}>
        <CalendarConnection />
      </Section>
      {band.role === 'OWNER' && (
        <Section title={t('settings.bandData')}>
          <AuditLog />
          <BandDataActions />
        </Section>
      )}
      <Section title={t('settings.sync')}>
        <Switch size="md" checked={paused} label={t('activity.pause')} description={t('activity.pauseHelp')}
          onChange={() => sync.mutate({ kind: paused ? 'resume' : 'pause' }, { onError: failed })} />
      </Section>
    </Stack>
  );
}
